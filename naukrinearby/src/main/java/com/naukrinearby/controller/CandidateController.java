package com.naukrinearby.controller;

import com.naukrinearby.exception.NotFoundException;
import com.naukrinearby.model.dto.CandidateProfileResponse;
import com.naukrinearby.model.dto.DashboardStatsDTO;
import com.naukrinearby.model.dto.NotificationPreferenceDto;
import com.naukrinearby.model.dto.ProfileUpdateRequest;
import com.naukrinearby.model.entity.CandidateProfile;
import com.naukrinearby.security.AuthPrincipal;
import com.naukrinearby.service.AccountService;
import com.naukrinearby.service.CandidateProfileService;
import com.naukrinearby.service.DashboardService;
import com.naukrinearby.service.NotificationPreferenceService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/candidate")
@PreAuthorize("hasRole('CANDIDATE')")
@RequiredArgsConstructor
public class CandidateController {

	private final CandidateProfileService profileService;
	private final NotificationPreferenceService preferenceService;
	private final DashboardService dashboardService;
	private final AccountService accountService;

	@GetMapping("/profile")
	public CandidateProfileResponse profile(@AuthenticationPrincipal AuthPrincipal principal) {
		CandidateProfile profile = profileService.getByUserId(principal.id());
		if (profile == null) {
			throw new NotFoundException("No profile yet — upload a resume to create one");
		}
		return CandidateProfileResponse.from(profile);
	}

	@PutMapping("/profile")
	public CandidateProfileResponse updateProfile(@RequestBody ProfileUpdateRequest req,
			@AuthenticationPrincipal AuthPrincipal principal) {
		return CandidateProfileResponse.from(profileService.updateProfile(principal.id(), req));
	}

	@GetMapping("/dashboard/stats")
	public DashboardStatsDTO dashboardStats(@AuthenticationPrincipal AuthPrincipal principal) {
		return dashboardService.candidateStats(principal.id());
	}

	/** DPDP right-to-erasure: deletes the account, cascades all data, and purges the resume object. */
	@DeleteMapping("/account")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteAccount(@AuthenticationPrincipal AuthPrincipal principal) {
		accountService.deleteAccount(principal.id());
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
