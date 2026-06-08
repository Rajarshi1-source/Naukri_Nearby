-- Transactional outbox for reliable PG -> ES sync (master plan §9.2, starter §1.1).
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,           -- "job"
    aggregate_id BIGINT NOT NULL,                  -- job_id
    event_type VARCHAR(50) NOT NULL,               -- JOB_CREATED | JOB_UPDATED | JOB_CLOSED
    payload TEXT NOT NULL,                          -- the ES document (pre-serialized JSON, forwarded as-is)
    version INT NOT NULL,                           -- aggregate version (stale-overwrite guard)
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSED | FAILED
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ
);
-- Cheap index on unprocessed events, oldest first (the sync worker polls this).
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';
