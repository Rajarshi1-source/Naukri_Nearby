package com.naukrinearby.model.entity;

import java.time.Instant;

import com.naukrinearby.model.enums.ApplicationStatus;

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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "applications", uniqueConstraints = @UniqueConstraint(columnNames = { "job_id", "candidate_id" }))
@Getter
@Setter
@NoArgsConstructor
public class Application {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "job_id", nullable = false)
	private Long jobId;

	@Column(name = "candidate_id", nullable = false)
	private Long candidateId;

	@Enumerated(EnumType.STRING)
	private ApplicationStatus status = ApplicationStatus.APPLIED;

	@Column(name = "cover_note", columnDefinition = "text")
	private String coverNote;

	@CreationTimestamp
	@Column(name = "applied_at", updatable = false)
	private Instant appliedAt;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private Instant updatedAt;

	public Application(Long jobId, Long candidateId, String coverNote) {
		this.jobId = jobId;
		this.candidateId = candidateId;
		this.coverNote = coverNote;
	}
}
