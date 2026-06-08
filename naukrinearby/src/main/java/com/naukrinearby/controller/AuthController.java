package com.naukrinearby.controller;

import com.naukrinearby.model.dto.auth.AuthResponse;
import com.naukrinearby.model.dto.auth.MeResponse;
import com.naukrinearby.model.dto.auth.RefreshRequest;
import com.naukrinearby.model.dto.auth.SendOtpRequest;
import com.naukrinearby.model.dto.auth.VerifyOtpRequest;
import com.naukrinearby.security.AuthPrincipal;
import com.naukrinearby.service.AuthService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@PostMapping("/send-otp")
	public ResponseEntity<Void> sendOtp(@Valid @RequestBody SendOtpRequest req) {
		authService.sendOtp(req.phone());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/verify-otp")
	public AuthResponse verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
		return authService.verifyOtp(req);
	}

	@PostMapping("/refresh")
	public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
		return authService.refresh(req.refreshToken());
	}

	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
		return MeResponse.from(authService.requireUser(principal.id()));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest req) {
		authService.logout(req == null ? null : req.refreshToken());
		return ResponseEntity.noContent().build();
	}
}
