package com.naukrinearby.worker;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.VersionType;
import tools.jackson.databind.ObjectMapper;
import com.naukrinearby.config.SearchProperties;
import com.naukrinearby.model.entity.OutboxEvent;
import com.naukrinearby.model.enums.ModerationStatus;
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
	private static final Set<String> ALERT_EVENTS = Set.of("JOB_CREATED", "JOB_APPROVED");

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
				boolean indexed = syncToElasticsearch(event);
				event.setStatus("PROCESSED");
				event.setProcessedAt(Instant.now());
				// Alerts go out when a job first becomes searchable: on create, or on admin approval of
				// a job that was held for review (ADR 0007).
				if (indexed && ALERT_EVENTS.contains(event.getEventType())) {
					events.publishEvent(new JobIndexedEvent(event.getAggregateId(), event.getEventType()));
				}
			}
			catch (ElasticsearchException ex) {
				// 409: a newer version is already indexed. 404: deleting a document that was never
				// indexed (e.g. a job held for review). Both mean the index already reflects this event.
				if (ex.status() == 409 || ex.status() == 404) {
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

	/**
	 * Applies one event to the index. Closed jobs and jobs that are not {@code APPROVED} are deleted
	 * (version-guarded, so a stale delete cannot remove a newer approved document); everything else is
	 * an idempotent, version-guarded upsert on {@code job_id}.
	 *
	 * @return true if the job is now indexed (searchable)
	 */
	@SuppressWarnings("unchecked")
	private boolean syncToElasticsearch(OutboxEvent event) throws Exception {
		String jobId = String.valueOf(event.getAggregateId());
		String index = searchProps.index();
		long version = event.getVersion() == null ? 1L : event.getVersion();

		Map<String, Object> doc = objectMapper.readValue(event.getPayload(), Map.class);
		Object moderation = doc.get("moderation_status");
		boolean hidden = "JOB_CLOSED".equals(event.getEventType())
				|| (moderation != null && !ModerationStatus.APPROVED.name().equals(moderation));
		if (hidden) {
			es.delete(d -> d.index(index).id(jobId).versionType(VersionType.External).version(version));
			return false;
		}

		// External version guard: ES rejects (409) any event older than the indexed document.
		es.index(i -> i
				.index(index)
				.id(jobId)
				.versionType(VersionType.External)
				.version(version)
				.document(doc));
		return true;
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
