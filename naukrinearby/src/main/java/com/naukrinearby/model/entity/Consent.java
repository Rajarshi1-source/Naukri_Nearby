package com.naukrinearby.model.entity;

import java.time.Instant;

import com.naukrinearby.model.enums.ConsentPurpose;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * One entry in the append-only DPDP consent ledger. Rows are never updated: a change of mind is a new
 * row, and the latest row per (user, purpose) is the current state.
 */
@Entity
@Table(name = "consents")
@Getter
@NoArgsConstructor
public class Consent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private ConsentPurpose purpose;

	@Column(nullable = false, updatable = false)
	private boolean granted;

	@Column(name = "notice_version", nullable = false, updatable = false)
	private String noticeVersion;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public Consent(Long userId, ConsentPurpose purpose, boolean granted, String noticeVersion) {
		this.userId = userId;
		this.purpose = purpose;
		this.granted = granted;
		this.noticeVersion = noticeVersion;
	}
}
