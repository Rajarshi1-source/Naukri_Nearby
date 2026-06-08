package com.naukrinearby.model.dto.job;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.naukrinearby.model.entity.Job;
import com.naukrinearby.util.GeoUtils;

public record JobResponse(
		Long id,
		String title,
		String slug,
		String description,
		String category,
		List<String> skillsRequired,
		String city,
		String state,
		String pincode,
		String companyName,
		Integer salaryMin,
		Integer salaryMax,
		String salaryType,
		String status,
		Integer radiusKm,
		Double lat,
		Double lng,
		Integer applicationCount,
		Instant createdAt) {

	public static JobResponse from(Job j) {
		Double lat = j.getLocation() == null ? null : GeoUtils.latOf(j.getLocation());
		Double lng = j.getLocation() == null ? null : GeoUtils.lngOf(j.getLocation());
		String company = j.getEmployer() == null ? null : j.getEmployer().getCompanyName();
		return new JobResponse(
				j.getId(), j.getTitle(), j.getSlug(), j.getDescription(),
				j.getCategory() == null ? null : j.getCategory().name(),
				j.getSkillsRequired() == null ? List.of() : Arrays.asList(j.getSkillsRequired()),
				j.getCity(), j.getState(), j.getPincode(), company,
				j.getSalaryMin(), j.getSalaryMax(), j.getSalaryType(),
				j.getStatus() == null ? null : j.getStatus().name(),
				j.getRadiusKm(), lat, lng, j.getApplicationCount(), j.getCreatedAt());
	}
}
