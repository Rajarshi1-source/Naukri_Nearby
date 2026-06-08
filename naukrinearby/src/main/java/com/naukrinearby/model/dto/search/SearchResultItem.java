package com.naukrinearby.model.dto.search;

public record SearchResultItem(
		Long id,
		String title,
		String slug,
		String category,
		String city,
		String companyName,
		Integer salaryMin,
		Integer salaryMax,
		Double distanceKm) {
}
