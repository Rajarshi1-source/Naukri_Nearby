# NaukriNearby — Operations Runbook

Backend operational procedures. Deployments split into an **API** tier (`SPRING_PROFILES_ACTIVE=api`,
workers off) and a **worker** tier (`worker`, runs outbox sync + notification consumer). See `k8s/`.

## Observability
- Start the stack: `docker compose --profile observability up -d` (Prometheus :9090, Grafana :3001,
  Langfuse :3002).
- Grafana auto-loads the **NaukriNearby — KPIs** dashboard: search latency, resume-parse latency,
  WhatsApp alerts/min, and notification queue depth.
- Key metrics: `search_duration_seconds`, `resume_parse_duration_seconds`, `notifications_sent_total`,
  `notification_queue_depth`, `notification_dlq_depth`, plus Resilience4j circuit-breaker metrics and
  `/actuator/circuitbreakers`.

## Outbox lag / failures (ADR 0001)
Symptoms: search results stale; `notification_queue_depth` flat while jobs are created.
1. Check `PENDING` backlog: `SELECT status, count(*) FROM outbox_events GROUP BY status;`
2. If many `PENDING` and ES is up → check the worker pod logs / that a worker pod is running.
3. If rows are `FAILED` (≥ MAX_RETRIES): inspect the payload, fix the root cause (mapping/ES), then
   reset for replay: `UPDATE outbox_events SET status='PENDING', retry_count=0 WHERE status='FAILED';`
4. The nightly `reconcile()` job logs the `FAILED` count — alert on it.

## DLQ drain (notifications)
Symptoms: `notification_dlq_depth > 0`.
1. Inspect: `XRANGE notification-dlq - +` in redis-cli.
2. After fixing the cause (e.g. Twilio creds), replay by re-adding entries to `notification-jobs`.
3. Idempotency (SETNX + unique `notification_logs` row) makes replay safe — no double sends.

## Zero-downtime reindex (ADR 0003)
1. Create `jobs_v2` with the new mapping/analyzer.
2. `POST /_reindex {"source":{"index":"jobs_v1"},"dest":{"index":"jobs_v2"}}`.
3. Flip atomically:
   `POST /_aliases {"actions":[{"remove":{"index":"jobs_v1","alias":"jobs"}},{"add":{"index":"jobs_v2","alias":"jobs"}}]}`
4. Verify, then `DELETE /jobs_v1`. Search degrades to PostGIS automatically if ES is unavailable mid-flip.

## Switching to real providers (ADR 0002)
Set the relevant `*_PROVIDER` env + keys (see `.env.example` / `k8s/config-and-secrets.example.yaml`)
and restart. Embedding dims must stay 1024 to match the pgvector column. No code change required.

## Right-to-erasure (DPDP)
`DELETE /api/candidate/account` cascades all DB rows and purges the resume object from MinIO. To erase
on request from ops, delete the `users` row (FKs cascade) and call `FileStorageService.delete(key)` for
the stored `resume_file_key`.
