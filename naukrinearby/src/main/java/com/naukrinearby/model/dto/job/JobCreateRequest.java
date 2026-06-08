package com.naukrinearby.model.dto.job;

import java.util.List;

import com.naukrinearby.model.enums.JobCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record JobCreateRequest(
		@NotBlank String title,
		@NotBlank String description,
		@NotNull JobCategory category,
		List<String> skillsRequired,
		Integer salaryMin,
		Integer salaryMax,
		String salaryType,
		Integer experienceRequiredMonths,
		@NotNull Double lat,
		@NotNull Double lng,
		String address,
		@NotBlank String city,
		@NotBlank String state,
		String pincode,
		Integer radiusKm) {
}
