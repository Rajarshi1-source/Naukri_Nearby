package com.naukrinearby.controller;

import java.util.List;
import java.util.Map;

import com.naukrinearby.exception.NotFoundException;
import com.naukrinearby.model.dto.CandidateProfileResponse;
import com.naukrinearby.model.dto.ConsentDto;
import com.naukrinearby.model.dto.DashboardStatsDTO;
import com.naukrinearby.model.dto.NotificationPreferenceDto;
import com.naukrinearby.model.dto.ProfileUpdateRequest;
import com.naukrinearby.model.dto.SkillGapResponse;
import com.naukrinearby.model.dto.job.JobResponse;
import com.naukrinearby.model.entity.CandidateProfile;
import com.naukrinearby.security.AuthPrincipal;
import com.naukrinearby.service.AccountService;
import com.naukrinearby.service.CandidateProfileService;
import com.naukrinearby.service.ConsentService;
import com.naukrinearby.service.DashboardService;
import com.naukrinearby.service.NotificationPreferenceService;
import com.naukrinearby.service.SavedJobService;
import com.naukrinearby.service.SkillGapService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
	private final SavedJobService savedJobService;
	private final ConsentService consentService;
	private final SkillGapService skillGapService;

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

	/** DPDP right to access: everything we hold about the candidate, as a downloadable JSON file. */
	@GetMapping("/account/export")
	public ResponseEntity<Map<String, Object>> exportAccount(@AuthenticationPrincipal AuthPrincipal principal) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"naukrinearby-my-data.json\"")
				.contentType(MediaType.APPLICATION_JSON)
				.body(accountService.export(principal.id()));
	}

	@GetMapping("/consents")
	public List<ConsentDto.Response> consents(@AuthenticationPrincipal AuthPrincipal principal) {
		return consentService.current(principal.id());
	}

	@PutMapping("/consents")
	public ConsentDto.Response updateConsent(@Valid @RequestBody ConsentDto.UpdateRequest req,
			@AuthenticationPrincipal AuthPrincipal principal) {
		return consentService.record(principal.id(), req.purpose(), req.granted(), ConsentService.WEB_NOTICE_VERSION);
	}

	/** Which of the job's skills the candidate has, which are missing, and where to learn them. */
	@GetMapping("/jobs/{jobId}/skill-gap")
	public SkillGapResponse skillGap(@PathVariable Long jobId, @AuthenticationPrincipal AuthPrincipal principal) {
		return skillGapService.forCandidate(principal.id(), jobId);
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

	@GetMapping("/saved-jobs")
	public List<JobResponse> savedJobs(@AuthenticationPrincipal AuthPrincipal principal) {
		return savedJobService.list(principal.id());
	}

	/** Save/bookmark a job. Idempotent — re-saving is a no-op. */
	@PostMapping("/saved-jobs/{jobId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void saveJob(@PathVariable Long jobId, @AuthenticationPrincipal AuthPrincipal principal) {
		savedJobService.save(principal.id(), jobId);
	}

	@DeleteMapping("/saved-jobs/{jobId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void unsaveJob(@PathVariable Long jobId, @AuthenticationPrincipal AuthPrincipal principal) {
		savedJobService.unsave(principal.id(), jobId);
	}
}
