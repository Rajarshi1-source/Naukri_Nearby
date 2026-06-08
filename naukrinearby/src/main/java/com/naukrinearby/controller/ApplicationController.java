package com.naukrinearby.controller;

import com.naukrinearby.model.dto.application.ApplicationResponse;
import com.naukrinearby.model.dto.application.ApplyRequest;
import com.naukrinearby.security.AuthPrincipal;
import com.naukrinearby.service.ApplicationService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ApplicationController {

	private final ApplicationService applicationService;

	@PostMapping("/api/jobs/{id}/apply")
	@PreAuthorize("hasRole('CANDIDATE')")
	@ResponseStatus(HttpStatus.CREATED)
	public ApplicationResponse apply(@PathVariable("id") Long jobId,
			@RequestBody(required = false) ApplyRequest req,
			@AuthenticationPrincipal AuthPrincipal principal) {
		String coverNote = req == null ? null : req.coverNote();
		return ApplicationResponse.from(applicationService.apply(jobId, principal.id(), coverNote));
	}

	@GetMapping("/api/candidate/applications")
	@PreAuthorize("hasRole('CANDIDATE')")
	public Page<ApplicationResponse> myApplications(Pageable pageable,
			@AuthenticationPrincipal AuthPrincipal principal) {
		return applicationService.myApplications(principal.id(), pageable).map(ApplicationResponse::from);
	}

	@GetMapping("/api/employer/jobs/{id}/applications")
	@PreAuthorize("hasRole('EMPLOYER')")
	public Page<ApplicationResponse> applicants(@PathVariable("id") Long jobId, Pageable pageable,
			@AuthenticationPrincipal AuthPrincipal principal) {
		return applicationService.applicantsForJob(jobId, principal.id(), pageable)
				.map(ApplicationResponse::from);
	}
}
