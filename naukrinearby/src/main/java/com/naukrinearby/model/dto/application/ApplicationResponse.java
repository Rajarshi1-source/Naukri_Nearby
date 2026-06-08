package com.naukrinearby.model.dto.application;

import java.time.Instant;

import com.naukrinearby.model.entity.Application;

public record ApplicationResponse(
		Long id,
		Long jobId,
		Long candidateId,
		String status,
		String coverNote,
		Instant appliedAt) {

	public static ApplicationResponse from(Application a) {
		return new ApplicationResponse(a.getId(), a.getJobId(), a.getCandidateId(),
				a.getStatus() == null ? null : a.getStatus().name(), a.getCoverNote(), a.getAppliedAt());
	}
}
