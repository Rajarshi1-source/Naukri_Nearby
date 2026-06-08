package com.naukrinearby.controller;

import com.naukrinearby.model.dto.job.JobCreateRequest;
import com.naukrinearby.model.dto.job.JobResponse;
import com.naukrinearby.model.dto.job.JobStatusUpdateRequest;
import com.naukrinearby.model.dto.job.JobUpdateRequest;
import com.naukrinearby.security.AuthPrincipal;
import com.naukrinearby.service.JobService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JobController {

	private final JobService jobService;

	@PostMapping("/api/jobs")
	@PreAuthorize("hasRole('EMPLOYER')")
	@ResponseStatus(HttpStatus.CREATED)
	public JobResponse create(@Valid @RequestBody JobCreateRequest req,
			@AuthenticationPrincipal AuthPrincipal principal) {
		return JobResponse.from(jobService.createJob(req, principal.id()));
	}

	@PutMapping("/api/jobs/{id}")
	@PreAuthorize("hasRole('EMPLOYER')")
	public JobResponse update(@PathVariable Long id, @Valid @RequestBody JobUpdateRequest req,
			@AuthenticationPrincipal AuthPrincipal principal) {
		return JobResponse.from(jobService.updateJob(id, req, principal.id()));
	}

	@PatchMapping("/api/jobs/{id}/status")
	@PreAuthorize("hasRole('EMPLOYER')")
	public JobResponse updateStatus(@PathVariable Long id, @Valid @RequestBody JobStatusUpdateRequest req,
			@AuthenticationPrincipal AuthPrincipal principal) {
		return JobResponse.from(jobService.updateStatus(id, req.status(), principal.id()));
	}

	/** Read-your-writes: the employer's own jobs are read from Postgres, not ES (master plan §9.5). */
	@GetMapping("/api/employer/jobs")
	@PreAuthorize("hasRole('EMPLOYER')")
	public Page<JobResponse> myJobs(Pageable pageable, @AuthenticationPrincipal AuthPrincipal principal) {
		return jobService.employerJobs(principal.id(), pageable).map(JobResponse::from);
	}
}
