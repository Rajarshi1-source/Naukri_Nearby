package com.naukrinearby.model.dto;

import java.time.Instant;

import com.naukrinearby.model.dto.job.JobResponse;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.model.enums.ModerationStatus;
import com.naukrinearby.model.enums.ReportReason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Report and moderation request/response models (ADR 0007). */
public final class ModerationDto {

	private ModerationDto() {
	}

	public record ReportRequest(@NotNull ReportReason reason, @Size(max = 1000) String details) {
	}

	/** {@code underReview} tells the reporter their report pushed the job into the review queue. */
	public record ReportResponse(boolean reported, boolean underReview) {
	}

	public record DecisionRequest(@NotNull ModerationStatus decision, @Size(max = 1000) String note) {
	}

	/** One row in the admin queue: the job with its risk data, plus how many users reported it. */
	public record QueueItem(JobResponse job, long reportCount, String moderationNote, Instant moderatedAt) {

		public static QueueItem of(Job job, long reports) {
			return new QueueItem(JobResponse.forOwner(job), reports, job.getModerationNote(), job.getModeratedAt());
		}
	}
}
