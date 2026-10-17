package com.naukrinearby.model.dto;

import java.time.Instant;

import com.naukrinearby.model.entity.Employer;
import com.naukrinearby.model.enums.TrustLevel;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Employer profile read/write models. The GSTIN drives {@code BUSINESS_VERIFIED} (ADR 0007). */
public final class EmployerProfileDto {

	private EmployerProfileDto() {
	}

	public record Request(
			@NotBlank @Size(max = 300) String companyName,
			@Size(max = 50) String companyType,
			@Size(max = 20) String gstNumber,
			@Size(max = 500) String address,
			@Size(max = 100) String city,
			@Size(max = 100) String state) {
	}

	public record Response(
			String companyName,
			String companyType,
			String gstNumber,
			boolean gstVerified,
			String trustLevel,
			String address,
			String city,
			String state,
			Instant createdAt,
			Instant verifiedAt) {

		public static Response from(Employer e) {
			return new Response(e.getCompanyName(), e.getCompanyType(), e.getGstNumber(), e.isGstVerified(),
					TrustLevel.of(e).name(), e.getAddress(), e.getCity(), e.getState(), e.getCreatedAt(),
					e.getVerifiedAt());
		}
	}
}
