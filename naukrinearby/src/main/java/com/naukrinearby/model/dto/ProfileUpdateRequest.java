package com.naukrinearby.model.dto;

import java.util.List;

/**
 * Manual candidate-profile edit (master plan §7.3 PUT /api/candidate/profile). All fields optional;
 * only non-null values are applied. Skills are owned by the resume pipeline and are not edited here.
 * Supplying {@code lat}/{@code lng} (from the map picker) updates the profile location.
 */
public record ProfileUpdateRequest(
		String name,
		String email,
		String city,
		String state,
		Double lat,
		Double lng,
		Integer preferredRadiusKm,
		List<String> preferredCategories,
		List<String> languagesSpoken) {
}
