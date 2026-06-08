package com.naukrinearby.model.dto.application;

import jakarta.validation.constraints.NotBlank;

/** Employer-driven application status change (master plan §7.3 PATCH /api/applications/{id}/status). */
public record ApplicationStatusUpdateRequest(@NotBlank String status) {
}
