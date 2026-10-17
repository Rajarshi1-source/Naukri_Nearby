package com.naukrinearby.model.elasticsearch;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.model.enums.ModerationStatus;
import com.naukrinearby.model.enums.TrustLevel;
import com.naukrinearby.util.GeoUtils;

/** The Elasticsearch document shape for a job (master plan §8.4). Serialized into outbox payloads. */
public record JobDocument(
		Long id,
		String title,
		String description,
		String category,
		List<String> skills,
		String city,
		String state,
		String pincode,
		@JsonProperty("salary_min") Integer salaryMin,
		@JsonProperty("salary_max") Integer salaryMax,
		String status,
		@JsonProperty("company_name") String companyName,
		GeoPoint location,
		@JsonProperty("created_at") Instant createdAt,
		String slug,
		@JsonProperty("moderation_status") String moderationStatus,
		@JsonProperty("trust_level") String trustLevel) {

	/** Elasticsearch geo_point — serialized as {"lat":..,"lon":..}. */
	public record GeoPoint(double lat, double lon) {
	}

	public static JobDocument from(Job j) {
		GeoPoint loc = j.getLocation() == null ? null
				: new GeoPoint(GeoUtils.latOf(j.getLocation()), GeoUtils.lngOf(j.getLocation()));
		String company = j.getEmployer() == null ? null : j.getEmployer().getCompanyName();
		return new JobDocument(
				j.getId(), j.getTitle(), j.getDescription(),
				j.getCategory() == null ? null : j.getCategory().name(),
				j.getSkillsRequired() == null ? List.of() : Arrays.asList(j.getSkillsRequired()),
				j.getCity(), j.getState(), j.getPincode(),
				j.getSalaryMin(), j.getSalaryMax(),
				j.getStatus() == null ? null : j.getStatus().name(),
				company, loc, j.getCreatedAt(), j.getSlug(),
				j.getModerationStatus() == null ? ModerationStatus.APPROVED.name() : j.getModerationStatus().name(),
				TrustLevel.of(j.getEmployer()).name());
	}
}
