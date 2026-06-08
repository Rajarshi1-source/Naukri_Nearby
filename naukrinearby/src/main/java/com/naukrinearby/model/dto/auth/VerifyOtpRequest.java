package com.naukrinearby.model.dto.auth;

import com.naukrinearby.model.enums.UserRole;

import jakarta.validation.constraints.NotBlank;

public record VerifyOtpRequest(
		@NotBlank String phone,
		@NotBlank String code,
		UserRole role) {

	public UserRole roleOrDefault() {
		return role == null ? UserRole.CANDIDATE : role;
	}
}
