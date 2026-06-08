package com.naukrinearby.model.dto.job;

import com.naukrinearby.model.enums.JobStatus;

import jakarta.validation.constraints.NotNull;

public record JobStatusUpdateRequest(@NotNull JobStatus status) {
}
