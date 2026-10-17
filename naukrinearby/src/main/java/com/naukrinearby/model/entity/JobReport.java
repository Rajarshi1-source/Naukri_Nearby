package com.naukrinearby.model.entity;

import java.time.Instant;

import com.naukrinearby.model.enums.ReportReason;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** A user's report against a job posting. One per user per job ({@code UNIQUE(job_id, reporter_id)}). */
@Entity
@Table(name = "job_reports", uniqueConstraints = @UniqueConstraint(columnNames = { "job_id", "reporter_id" }))
@Getter
@Setter
@NoArgsConstructor
public class JobReport {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "job_id", nullable = false)
	private Long jobId;

	@Column(name = "reporter_id", nullable = false)
	private Long reporterId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReportReason reason;

	@Column(columnDefinition = "text")
	private String details;

	@CreationTimestamp
	@Column(name = "created_at", updatable = false, nullable = false)
	private Instant createdAt;

	public JobReport(Long jobId, Long reporterId, ReportReason reason, String details) {
		this.jobId = jobId;
		this.reporterId = reporterId;
		this.reason = reason;
		this.details = details;
	}
}
