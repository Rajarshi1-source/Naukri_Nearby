package com.naukrinearby.controller;

import com.naukrinearby.model.dto.ModerationDto;
import com.naukrinearby.model.dto.PageResponse;
import com.naukrinearby.model.dto.job.JobResponse;
import com.naukrinearby.model.enums.ModerationStatus;
import com.naukrinearby.security.AuthPrincipal;
import com.naukrinearby.service.ModerationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Job reports (any signed-in user) and the admin moderation queue (ADR 0007). */
@RestController
@RequiredArgsConstructor
public class ModerationController {

	private final ModerationService moderationService;

	@PostMapping("/api/jobs/{id}/report")
	@PreAuthorize("isAuthenticated()")
	@ResponseStatus(HttpStatus.CREATED)
	public ModerationDto.ReportResponse report(@PathVariable Long id,
			@Valid @RequestBody ModerationDto.ReportRequest req,
			@AuthenticationPrincipal AuthPrincipal principal) {
		boolean escalated = moderationService.report(id, principal.id(), req.reason(), req.details());
		return new ModerationDto.ReportResponse(true, escalated);
	}

	@GetMapping("/api/admin/moderation/jobs")
	@PreAuthorize("hasRole('ADMIN')")
	public PageResponse<ModerationDto.QueueItem> queue(
			@RequestParam(required = false) ModerationStatus status, Pageable pageable) {
		return PageResponse.from(moderationService.queue(status, pageable),
				job -> ModerationDto.QueueItem.of(job, moderationService.reportCount(job.getId())));
	}

	@PostMapping("/api/admin/moderation/jobs/{id}/decision")
	@PreAuthorize("hasRole('ADMIN')")
	public JobResponse decide(@PathVariable Long id, @Valid @RequestBody ModerationDto.DecisionRequest req) {
		return JobResponse.forOwner(moderationService.decide(id, req.decision(), req.note()));
	}
}
