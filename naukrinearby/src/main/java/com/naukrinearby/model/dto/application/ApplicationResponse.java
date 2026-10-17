package com.naukrinearby.model.dto.application;

import java.time.Instant;

import com.naukrinearby.model.entity.Application;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.model.entity.User;

/**
 * An application. {@code jobTitle}/{@code jobSlug} are filled for the candidate's own list;
 * {@code candidateName}/{@code candidatePhone} only for the employer who owns the job, who needs them to
 * contact the applicant. Both are null elsewhere.
 */
public record ApplicationResponse(
		Long id,
		Long jobId,
		Long candidateId,
		String status,
		String coverNote,
		Instant appliedAt,
		String jobTitle,
		String jobSlug,
		String candidateName,
		String candidatePhone) {

	public static ApplicationResponse from(Application a) {
		return from(a, null, null);
	}

	public static ApplicationResponse from(Application a, Job job, User candidate) {
		return new ApplicationResponse(a.getId(), a.getJobId(), a.getCandidateId(),
				a.getStatus() == null ? null : a.getStatus().name(), a.getCoverNote(), a.getAppliedAt(),
				job == null ? null : job.getTitle(),
				job == null ? null : job.getSlug(),
				candidate == null ? null : candidate.getName(),
				candidate == null ? null : candidate.getPhone());
	}
}
