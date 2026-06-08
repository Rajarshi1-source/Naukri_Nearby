package com.naukrinearby.controller;

import com.naukrinearby.model.dto.NotificationPreferenceDto;
import com.naukrinearby.security.AuthPrincipal;
import com.naukrinearby.service.NotificationPreferenceService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Spec-aligned notification-preference endpoints (master plan §7.3:
 * {@code GET/PUT /api/notifications/preferences}). These delegate to the same
 * {@link NotificationPreferenceService} as the candidate-scoped paths. Because this base path is
 * outside {@code /api/candidate/**}, the candidate role is enforced explicitly via {@code @PreAuthorize}.
 */
@RestController
@RequestMapping("/api/notifications")
@PreAuthorize("hasRole('CANDIDATE')")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationPreferenceService preferenceService;

	@GetMapping("/preferences")
	public NotificationPreferenceDto getPreferences(@AuthenticationPrincipal AuthPrincipal principal) {
		return NotificationPreferenceDto.from(preferenceService.getOrCreate(principal.id()));
	}

	@PutMapping("/preferences")
	public NotificationPreferenceDto updatePreferences(@RequestBody NotificationPreferenceDto req,
			@AuthenticationPrincipal AuthPrincipal principal) {
		return NotificationPreferenceDto.from(preferenceService.update(principal.id(), req));
	}
}
