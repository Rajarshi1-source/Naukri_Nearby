package com.naukrinearby.service;

import java.time.Duration;

import com.naukrinearby.config.AdminProperties;
import com.naukrinearby.config.OtpProperties;
import com.naukrinearby.exception.RateLimitException;
import com.naukrinearby.exception.ValidationException;
import com.naukrinearby.model.dto.auth.AuthResponse;
import com.naukrinearby.model.dto.auth.VerifyOtpRequest;
import com.naukrinearby.model.entity.Employer;
import com.naukrinearby.model.entity.NotificationPreference;
import com.naukrinearby.model.entity.User;
import com.naukrinearby.model.enums.Language;
import com.naukrinearby.model.enums.UserRole;
import com.naukrinearby.repository.EmployerRepository;
import com.naukrinearby.repository.NotificationPreferenceRepository;
import com.naukrinearby.repository.UserRepository;
import com.naukrinearby.security.JwtService;
import com.naukrinearby.service.otp.OtpProvider;
import com.naukrinearby.util.PhoneNumberValidator;
import com.naukrinearby.util.RedisRateLimiter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

	private final OtpProvider otpProvider;
	private final RedisRateLimiter rateLimiter;
	private final OtpProperties otpProps;
	private final UserRepository userRepo;
	private final EmployerRepository employerRepo;
	private final NotificationPreferenceRepository prefRepo;
	private final JwtService jwtService;
	private final AdminProperties adminProps;

	public void sendOtp(String rawPhone) {
		String phone = PhoneNumberValidator.normalize(rawPhone);
		if (!rateLimiter.tryAcquire("otp:attempts:" + phone, otpProps.maxPerHour(), Duration.ofHours(1))) {
			throw new RateLimitException("Too many OTP requests. Try again later.");
		}
		otpProvider.send(phone);
	}

	@Transactional
	public AuthResponse verifyOtp(VerifyOtpRequest req) {
		String phone = PhoneNumberValidator.normalize(req.phone());
		if (!otpProvider.verify(phone, req.code())) {
			throw new ValidationException("Invalid or expired OTP");
		}
		User user = userRepo.findByPhone(phone)
				.orElseGet(() -> createUser(phone, isAdminPhone(phone) ? UserRole.ADMIN : req.roleOrDefault()));
		boolean changed = false;
		if (!user.isVerified()) {
			user.setVerified(true);
			changed = true;
		}
		// Admin bootstrap: the only way to get ROLE_ADMIN is to be listed in naukri.admin.phones.
		if (isAdminPhone(phone) && user.getRole() != UserRole.ADMIN) {
			user.setRole(UserRole.ADMIN);
			changed = true;
		}
		if (changed) {
			userRepo.save(user);
		}
		String access = jwtService.issueAccessToken(user);
		String refresh = jwtService.issueRefreshToken(user.getId());
		return new AuthResponse(access, refresh, user.getRole().name());
	}

	public AuthResponse refresh(String refreshToken) {
		String newToken = java.util.UUID.randomUUID().toString();
		Long userId = jwtService.rotateRefreshToken(refreshToken, newToken);
		if (userId == null) {
			throw new ValidationException("Invalid refresh token");
		}
		User user = userRepo.findById(userId)
				.orElseThrow(() -> new ValidationException("Invalid refresh token"));
		return new AuthResponse(jwtService.issueAccessToken(user), newToken, user.getRole().name());
	}

	public void logout(String refreshToken) {
		if (refreshToken != null) {
			jwtService.revokeRefreshToken(refreshToken);
		}
	}

	@Transactional
	public void updateLanguage(Long userId, String language) {
		User user = requireUser(userId);
		user.setPreferredLanguage(Language.fromCode(language).getCode());
		userRepo.save(user);
	}

	public User requireUser(Long id) {
		return userRepo.findById(id).orElseThrow(() -> new ValidationException("Unknown user"));
	}

	/** Finds or creates a verified candidate by phone — used by the WhatsApp-first apply flow. */
	@Transactional
	public User getOrCreateCandidate(String normalizedPhone) {
		User user = userRepo.findByPhone(normalizedPhone)
				.orElseGet(() -> createUser(normalizedPhone, UserRole.CANDIDATE));
		if (!user.isVerified()) {
			user.setVerified(true);
			userRepo.save(user);
		}
		return user;
	}

	private boolean isAdminPhone(String normalizedPhone) {
		for (String candidate : adminProps.phones()) {
			try {
				if (PhoneNumberValidator.normalize(candidate).equals(normalizedPhone)) {
					return true;
				}
			}
			catch (RuntimeException ignored) {
				// A malformed entry in NAUKRI_ADMIN_PHONES never grants admin.
			}
		}
		return false;
	}

	private User createUser(String phone, UserRole role) {
		User user = userRepo.save(new User(phone, role));
		if (role == UserRole.EMPLOYER) {
			Employer employer = new Employer();
			employer.setUserId(user.getId());
			employer.setCompanyName("Employer-" + phone);
			employerRepo.save(employer);
		}
		else if (role == UserRole.CANDIDATE) {
			NotificationPreference pref = new NotificationPreference();
			pref.setUserId(user.getId());
			prefRepo.save(pref);
		}
		return user;
	}
}
