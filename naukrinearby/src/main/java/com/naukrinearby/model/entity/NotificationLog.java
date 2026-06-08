package com.naukrinearby.model.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "notification_logs",
		uniqueConstraints = @UniqueConstraint(columnNames = { "candidate_id", "job_id" }))
@Getter
@Setter
@NoArgsConstructor
public class NotificationLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "candidate_id")
	private Long candidateId;

	@Column(name = "job_id")
	private Long jobId;

	@Column(nullable = false)
	private String channel;

	@Column(nullable = false)
	private String status;

	@Column(name = "message_sid")
	private String messageSid;

	@Column(name = "translated_text", columnDefinition = "text")
	private String translatedText;

	private String language;

	@CreationTimestamp
	@Column(name = "sent_at", updatable = false)
	private Instant sentAt;

	@Column(name = "delivered_at")
	private Instant deliveredAt;

	@Column(name = "read_at")
	private Instant readAt;

	public static NotificationLog of(Long candidateId, Long jobId, String channel, String status,
			String translatedText, String language) {
		NotificationLog log = new NotificationLog();
		log.candidateId = candidateId;
		log.jobId = jobId;
		log.channel = channel;
		log.status = status;
		log.translatedText = translatedText;
		log.language = language;
		return log;
	}
}
