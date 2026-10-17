package com.naukrinearby.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.naukrinearby.exception.NotFoundException;
import com.naukrinearby.model.dto.CandidateProfileResponse;
import com.naukrinearby.model.dto.NotificationPreferenceDto;
import com.naukrinearby.model.dto.application.ApplicationResponse;
import com.naukrinearby.model.entity.CandidateProfile;
import com.naukrinearby.model.entity.SavedJob;
import com.naukrinearby.model.entity.User;
import com.naukrinearby.repository.ApplicationRepository;
import com.naukrinearby.repository.CandidateProfileRepository;
import com.naukrinearby.repository.NotificationLogRepository;
import com.naukrinearby.repository.NotificationPreferenceRepository;
import com.naukrinearby.repository.SavedJobRepository;
import com.naukrinearby.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account lifecycle and DPDP data-principal rights:
 * <ul>
 * <li>right to erasure — deleting a user cascades all owned rows (FKs are {@code ON DELETE CASCADE})
 * and purges the resume object from storage so no PII is left behind;</li>
 * <li>right to access — {@link #export} returns everything we hold about the user as one document.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

	private final UserRepository userRepo;
	private final CandidateProfileRepository profileRepo;
	private final ApplicationRepository applicationRepo;
	private final SavedJobRepository savedJobRepo;
	private final NotificationPreferenceRepository preferenceRepo;
	private final NotificationLogRepository notificationLogRepo;
	private final ConsentService consentService;
	private final FileStorageService fileStorage;

	@Transactional
	public void deleteAccount(Long userId) {
		String resumeKey = profileRepo.findByUserId(userId)
				.map(CandidateProfile::getResumeFileKey)
				.orElse(null);
		userRepo.deleteById(userId);
		if (resumeKey != null) {
			fileStorage.delete(resumeKey);
		}
		log.info("Erased account and resume object for user {}", userId);
	}

	/**
	 * Everything stored about a user, as a JSON-ready map. Internal data about other people (employer
	 * notes, risk scores) is never included. The stored resume file itself is referenced, not inlined;
	 * it stays downloadable through its signed URL flow.
	 */
	@Transactional(readOnly = true)
	public Map<String, Object> export(Long userId) {
		User user = userRepo.findById(userId).orElseThrow(() -> new NotFoundException("Unknown user"));
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("exportedAt", Instant.now());
		out.put("account", account(user));
		out.put("profile", profileRepo.findByUserId(userId).map(CandidateProfileResponse::from).orElse(null));
		out.put("applications", applicationRepo.findByCandidateIdOrderByAppliedAtDesc(userId, Pageable.unpaged())
				.map(ApplicationResponse::from).getContent());
		out.put("savedJobIds", savedJobRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
				.map(SavedJob::getJobId).toList());
		out.put("notificationPreferences", preferenceRepo.findByUserId(userId)
				.map(NotificationPreferenceDto::from).orElse(null));
		out.put("notificationsReceived", notificationLogRepo.findByCandidateIdOrderBySentAtDesc(userId).stream()
				.map(n -> {
					Map<String, Object> m = new LinkedHashMap<>();
					m.put("jobId", n.getJobId());
					m.put("channel", n.getChannel());
					m.put("status", n.getStatus());
					m.put("language", n.getLanguage());
					m.put("message", n.getTranslatedText());
					m.put("sentAt", n.getSentAt());
					return m;
				}).toList());
		out.put("consents", consentService.history(userId).stream()
				.map(c -> {
					Map<String, Object> m = new LinkedHashMap<>();
					m.put("purpose", c.getPurpose());
					m.put("granted", c.isGranted());
					m.put("noticeVersion", c.getNoticeVersion());
					m.put("recordedAt", c.getCreatedAt());
					return m;
				}).toList());
		return out;
	}

	private static Map<String, Object> account(User user) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("id", user.getId());
		m.put("phone", user.getPhone());
		m.put("role", user.getRole());
		m.put("name", user.getName());
		m.put("email", user.getEmail());
		m.put("preferredLanguage", user.getPreferredLanguage());
		m.put("createdAt", user.getCreatedAt());
		return m;
	}
}
