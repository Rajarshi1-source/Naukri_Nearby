package com.naukrinearby.model.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

/**
 * Candidate profile. The {@code skill_embedding vector(1024)} column is intentionally NOT mapped as
 * a JPA field — it is written and read via native pgvector queries (see CandidateProfileRepository).
 */
@Entity
@Table(name = "candidate_profiles")
@Getter
@Setter
@NoArgsConstructor
public class CandidateProfile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", unique = true)
	private Long userId;

	private String name;

	private String phone;

	private String email;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(columnDefinition = "text[]", nullable = false)
	private String[] skills = new String[0];

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private List<ExperienceItem> experience = new ArrayList<>();

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(columnDefinition = "jsonb")
	private List<EducationItem> education = new ArrayList<>();

	private String city;

	private String state;

	@Column(columnDefinition = "geography(Point,4326)")
	private Point location;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "languages_spoken", columnDefinition = "text[]")
	private String[] languagesSpoken = new String[] { "en" };

	@Column(name = "total_experience_months")
	private Integer totalExperienceMonths = 0;

	@Column(name = "preferred_radius_km")
	private Integer preferredRadiusKm = 10;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "preferred_categories", columnDefinition = "text[]")
	private String[] preferredCategories = new String[0];

	@Column(name = "resume_file_key")
	private String resumeFileKey;

	@Column(name = "resume_parsed_at")
	private Instant resumeParsedAt;

	@Column(name = "profile_completeness")
	private Integer profileCompleteness = 0;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private Instant updatedAt;

	@Data
	public static class ExperienceItem {
		private String title;
		private String company;
		private Integer months;
	}

	@Data
	public static class EducationItem {
		private String degree;
		private String institution;
		private Integer year;
	}
}
