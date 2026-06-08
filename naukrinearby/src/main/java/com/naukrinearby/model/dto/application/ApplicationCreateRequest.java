package com.naukrinearby.model.dto.application;

import jakarta.validation.constraints.NotNull;

/** Spec-aligned apply payload (master plan §7.3): {@code POST /api/applications { jobId, coverNote? }}. */
public record ApplicationCreateRequest(@NotNull Long jobId, String coverNote) {
}
