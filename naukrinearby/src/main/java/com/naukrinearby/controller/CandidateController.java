package com.naukrinearby.controller;

import com.naukrinearby.exception.NotFoundException;
import com.naukrinearby.model.dto.CandidateProfileResponse;
import com.naukrinearby.model.dto.NotificationPreferenceDto;
import com.naukrinearby.model.entity.CandidateProfile;
import com.naukrinearby.security.AuthPrincipal;
import com.naukrinearby.service.CandidateProfileService;
import com.naukrinearby.service.NotificationPreferenceService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/candidate")
@PreAuthorize("hasRole('CANDIDATE')")
@RequiredArgsConstructor
public class CandidateController {

	private final CandidateProfileService profileService;
	private final NotificationPreferenceService preferenceService;

	@GetMapping("/profile")
	public CandidateProfileResponse profile(@AuthenticationPrincipal AuthPrincipal principal) {
		CandidateProfile profile = profileService.getByUserId(principal.id());
		if (profile == null) {
			throw new NotFoundException("No profile yet — upload a resume to create one");
		}
		return CandidateProfileResponse.from(profile);
	}

	@GetMapping("/notification-preferences")
	public NotificationPreferenceDto getPreferences(@AuthenticationPrincipal AuthPrincipal principal) {
		return NotificationPreferenceDto.from(preferenceService.getOrCreate(principal.id()));
	}

	@PutMapping("/notification-preferences")
	public NotificationPreferenceDto updatePreferences(@RequestBody NotificationPreferenceDto req,
			@AuthenticationPrincipal AuthPrincipal principal) {
		return NotificationPreferenceDto.from(preferenceService.update(principal.id(), req));
	}
}
