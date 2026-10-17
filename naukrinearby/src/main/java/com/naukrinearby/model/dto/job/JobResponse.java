package com.naukrinearby.model.dto.job;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.naukrinearby.model.entity.Employer;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.model.enums.TrustLevel;
import com.naukrinearby.util.GeoUtils;

/**
 * Job as returned by the API. {@code trustLevel} and {@code employerSince} are public trust signals.
 * {@code moderationStatus}, {@code riskScore} and {@code riskReasons} are filled only for the owning
 * employer and admins ({@link #forOwner}); candidates never see risk data (ADR 0007).
 */
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
		Instant createdAt,
		String trustLevel,
		Instant employerSince,
		String moderationStatus,
		Integer riskScore,
		List<String> riskReasons) {

	/** Public view: no moderation or risk data. */
	public static JobResponse from(Job j) {
		return build(j, false);
	}

	/** Owner/admin view: includes the moderation outcome and the risk reasons behind it. */
	public static JobResponse forOwner(Job j) {
		return build(j, true);
	}

	private static JobResponse build(Job j, boolean withRisk) {
		Double lat = j.getLocation() == null ? null : GeoUtils.latOf(j.getLocation());
		Double lng = j.getLocation() == null ? null : GeoUtils.lngOf(j.getLocation());
		Employer employer = j.getEmployer();
		String company = employer == null ? null : employer.getCompanyName();
		return new JobResponse(
				j.getId(), j.getTitle(), j.getSlug(), j.getDescription(),
				j.getCategory() == null ? null : j.getCategory().name(),
				j.getSkillsRequired() == null ? List.of() : Arrays.asList(j.getSkillsRequired()),
				j.getCity(), j.getState(), j.getPincode(), company,
				j.getSalaryMin(), j.getSalaryMax(), j.getSalaryType(),
				j.getStatus() == null ? null : j.getStatus().name(),
				j.getRadiusKm(), lat, lng, j.getApplicationCount(), j.getCreatedAt(),
				TrustLevel.of(employer).name(),
				employer == null ? null : employer.getCreatedAt(),
				withRisk && j.getModerationStatus() != null ? j.getModerationStatus().name() : null,
				withRisk ? j.getRiskScore() : null,
				withRisk && j.getRiskReasons() != null ? Arrays.asList(j.getRiskReasons()) : null);
	}
}
