package com.naukrinearby.worker;

/** Published after a job is (idempotently) indexed into Elasticsearch by the outbox sync worker. */
public record JobIndexedEvent(Long jobId, String eventType) {
}
