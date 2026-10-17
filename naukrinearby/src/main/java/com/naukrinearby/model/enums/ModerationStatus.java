package com.naukrinearby.model.enums;

/** Whether a job may appear in public reads (ADR 0007). Only {@code APPROVED} jobs are indexed. */
public enum ModerationStatus {
	APPROVED,
	PENDING_REVIEW,
	REJECTED
}
