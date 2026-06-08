package com.naukrinearby.model.dto.job;

import java.util.List;

import com.naukrinearby.model.enums.JobCategory;

/** Partial update — only non-null fields are applied. */
public record JobUpdateRequest(
		String title,
		String description,
		JobCategory category,
		List<String> skillsRequired,
		Integer salaryMin,
		Integer salaryMax,
		String salaryType,
		Integer experienceRequiredMonths,
		Integer radiusKm) {
}
