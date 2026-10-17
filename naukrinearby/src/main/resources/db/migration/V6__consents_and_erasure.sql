-- DPDP notice-and-consent ledger. Append-only: the latest row per (user, purpose) is the current state.
-- Rows are erased with the user (ON DELETE CASCADE) as part of right-to-erasure.
CREATE TABLE consents (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(40) NOT NULL,
    granted BOOLEAN NOT NULL,
    notice_version VARCHAR(40) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_consents_user_purpose ON consents (user_id, purpose, created_at DESC, id DESC);

-- Right-to-erasure fix: notification_logs referenced users and jobs without ON DELETE CASCADE, so
-- deleting a user who had received an alert (or an employer whose job had alerts) failed. Re-create
-- both foreign keys with CASCADE. Compatible with the previous release (no column changes).
ALTER TABLE notification_logs DROP CONSTRAINT IF EXISTS notification_logs_candidate_id_fkey;
ALTER TABLE notification_logs ADD CONSTRAINT notification_logs_candidate_id_fkey
    FOREIGN KEY (candidate_id) REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE notification_logs DROP CONSTRAINT IF EXISTS notification_logs_job_id_fkey;
ALTER TABLE notification_logs ADD CONSTRAINT notification_logs_job_id_fkey
    FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE;
