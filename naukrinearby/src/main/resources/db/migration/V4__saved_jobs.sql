-- Saved/bookmarked jobs for candidates. Idempotent on UNIQUE(user_id, job_id).
CREATE TABLE saved_jobs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_id BIGINT NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(user_id, job_id)
);
CREATE INDEX idx_saved_jobs_user ON saved_jobs(user_id);
