package com.naukrinearby.model.entity;

import java.time.Instant;

import com.naukrinearby.model.enums.JobCategory;
import com.naukrinearby.model.enums.JobStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

/**
 * Job posting. The {@code requirement_vector vector(1024)} column is NOT mapped as a JPA field —
 * it is written/read via native pgvector queries (see JobRepository). The {@code version} column is
 * a domain version manually bumped on update (outbox stale-overwrite guard), NOT a JPA {@code @Version}.
 */
@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor
public class Job {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "employer_id")
	private Employer employer;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false, unique = true)
	private String slug;

	@Column(columnDefinition = "text")
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private JobCategory category;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "skills_required", columnDefinition = "text[]")
	private String[] skillsRequired = new String[0];

	@Column(name = "salary_min")
	private Integer salaryMin;

	@Column(name = "salary_max")
	private Integer salaryMax;

	@Column(name = "salary_type")
	private String salaryType = "MONTHLY";

	@Column(name = "experience_required_months")
	private Integer experienceRequiredMonths = 0;

	@Column(columnDefinition = "geography(Point,4326)")
	private Point location;

	private String address;

	@Column(nullable = false)
	private String city;

	@Column(nullable = false)
	private String state;

	private String pincode;

	@Column(name = "radius_km")
	private Integer radiusKm = 10;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private JobStatus status = JobStatus.ACTIVE;

	@Column(nullable = false)
	private Integer version = 1;

	@Column(name = "is_whatsapp_enabled")
	private boolean whatsappEnabled = true;

	@Column(name = "application_count")
	private Integer applicationCount = 0;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private Instant updatedAt;
}
