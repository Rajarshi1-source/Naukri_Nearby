package com.naukrinearby.model.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record SendOtpRequest(@NotBlank String phone) {
}
