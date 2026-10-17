-- Trust signals, fraud scoring and moderation (ADR 0007).
-- Expand-only (ADR 0004): defaulted columns and a new table, so pods on the previous release keep
-- working. Existing jobs default to APPROVED, which is what they effectively were.
ALTER TABLE jobs ADD COLUMN moderation_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE jobs ADD COLUMN risk_score INT NOT NULL DEFAULT 0;
ALTER TABLE jobs ADD COLUMN risk_reasons TEXT[] NOT NULL DEFAULT '{}';
ALTER TABLE jobs ADD COLUMN moderated_at TIMESTAMP;
ALTER TABLE jobs ADD COLUMN moderation_note TEXT;
-- The admin review queue reads only pending jobs, oldest first.
CREATE INDEX idx_job_moderation_pending ON jobs (created_at) WHERE moderation_status = 'PENDING_REVIEW';

ALTER TABLE employers ADD COLUMN gst_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE employers ADD COLUMN verified_at TIMESTAMP;

-- Community reports. One report per user per job; enough distinct reports escalate the job to review.
CREATE TABLE job_reports (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    reporter_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason VARCHAR(30) NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (job_id, reporter_id)
);
CREATE INDEX idx_job_reports_job ON job_reports (job_id, created_at);
