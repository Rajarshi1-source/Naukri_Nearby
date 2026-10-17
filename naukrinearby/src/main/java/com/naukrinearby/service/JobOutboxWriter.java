package com.naukrinearby.service;

import com.naukrinearby.model.elasticsearch.JobDocument;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.model.entity.OutboxEvent;
import com.naukrinearby.repository.OutboxRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the outbox event for a job change. Callers bump {@code job.version} and save the row first;
 * this must run in the caller's transaction so the row and the event commit together (ADR 0001).
 * Event types: {@code JOB_CREATED}, {@code JOB_UPDATED}, {@code JOB_CLOSED}, {@code JOB_APPROVED}.
 */
@Component
@RequiredArgsConstructor
public class JobOutboxWriter {

	private final OutboxRepository outboxRepo;
	private final ObjectMapper objectMapper;

	@Transactional(propagation = Propagation.MANDATORY)
	public void write(Job job, String eventType) {
		outboxRepo.save(OutboxEvent.builder()
				.aggregateType("job")
				.aggregateId(job.getId())
				.eventType(eventType)
				.version(job.getVersion())
				.payload(toJobDocumentJson(job))
				.status("PENDING")
				.build());
	}

	private String toJobDocumentJson(Job job) {
		try {
			return objectMapper.writeValueAsString(JobDocument.from(job));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to serialize job document", ex);
		}
	}
}
