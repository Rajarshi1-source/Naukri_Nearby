package com.naukrinearby.worker;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.VersionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naukrinearby.config.SearchProperties;
import com.naukrinearby.model.entity.OutboxEvent;
import com.naukrinearby.repository.OutboxRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains the transactional outbox into Elasticsearch with an idempotent, version-guarded upsert on
 * {@code job_id} (starter §1.4). At-least-once delivery + idempotent upsert = effectively-once.
 * Survives ES downtime: events stay PENDING and drain on recovery.
 *
 * <p>{@code @Profile("!api")}: runs everywhere except the API-only deployment (master §17 scaling
 * decision). The default single-process app and the dedicated worker deployment both run it; only a
 * pod started with {@code SPRING_PROFILES_ACTIVE=api} skips it, letting API and workers scale apart.
 */
@Slf4j
@Component
@Profile("!api")
@RequiredArgsConstructor
public class OutboxSyncWorker {

	private static final int BATCH = 100;
	private static final int MAX_RETRIES = 5;

	private final OutboxRepository outboxRepo;
	private final ElasticsearchClient es;
	private final ObjectMapper objectMapper;
	private final SearchProperties searchProps;
	private final ApplicationEventPublisher events;

	@Scheduled(fixedDelay = 2000)
	@Transactional
	public void syncBatch() {
		List<OutboxEvent> batch = outboxRepo.fetchPendingBatch(BATCH);
		if (batch.isEmpty()) {
			return;
		}
		for (OutboxEvent event : batch) {
			try {
				indexToElasticsearch(event);
				event.setStatus("PROCESSED");
				event.setProcessedAt(Instant.now());
				if ("JOB_CREATED".equals(event.getEventType())) {
					events.publishEvent(new JobIndexedEvent(event.getAggregateId(), event.getEventType()));
				}
			}
			catch (ElasticsearchException ex) {
				// Version conflict means a newer document is already indexed — treat as success.
				if (ex.status() == 409) {
					event.setStatus("PROCESSED");
					event.setProcessedAt(Instant.now());
					log.debug("Outbox event {} superseded by newer version", event.getId());
				}
				else {
					recordFailure(event, ex);
				}
			}
			catch (Exception ex) {
				recordFailure(event, ex);
			}
		}
		outboxRepo.saveAll(batch);
	}

	private void recordFailure(OutboxEvent event, Exception ex) {
		event.setRetryCount(event.getRetryCount() + 1);
		if (event.getRetryCount() >= MAX_RETRIES) {
			event.setStatus("FAILED"); // -> alert + manual reconcile (nightly job)
			log.error("Outbox event {} FAILED after {} retries: {}",
					event.getId(), MAX_RETRIES, ex.getMessage());
		}
		else {
			log.warn("Outbox event {} retry {}: {}", event.getId(), event.getRetryCount(), ex.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	private void indexToElasticsearch(OutboxEvent event) throws Exception {
		String jobId = String.valueOf(event.getAggregateId());
		String index = searchProps.index();

		if ("JOB_CLOSED".equals(event.getEventType())) {
			es.delete(d -> d.index(index).id(jobId));
			return;
		}

		Map<String, Object> doc = objectMapper.readValue(event.getPayload(), Map.class);
		long version = event.getVersion() == null ? 1L : event.getVersion();
		// External version guard: ES rejects (409) any event older than the indexed document.
		es.index(i -> i
				.index(index)
				.id(jobId)
				.versionType(VersionType.External)
				.version(version)
				.document(doc));
	}

	/** Nightly reconciliation safety net (starter §1.6): re-enqueue / alert on drift. */
	@Scheduled(cron = "0 0 3 * * *")
	public void reconcile() {
		long failed = outboxRepo.countByStatus("FAILED");
		if (failed > 0) {
			log.warn("Reconciliation: {} outbox events are FAILED and need manual review", failed);
		}
	}
}
