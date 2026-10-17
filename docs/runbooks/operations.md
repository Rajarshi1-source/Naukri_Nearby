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

### Actuator ports and access
Actuator runs on a separate **management port** (`management.server.port`, env `MANAGEMENT_PORT`,
default **8081**). The public API port (8080) serves no actuator endpoints.

| Endpoint (port 8081) | Access |
| --- | --- |
| `/actuator/health/**` (liveness/readiness), `/actuator/info` | public (k8s probes, CI wait loop) |
| `/actuator/prometheus` | anonymous **only on 8081** (Prometheus scrape); ADMIN-only on any other port |
| everything else (`metrics`, `circuitbreakers`, `retries`, …) | `ROLE_ADMIN` JWT |

Rules that keep the scrape safe:
- **Never publish 8081.** Compose doesn't run the app; on the host, firewall 8081 to the Prometheus
  host. In k8s the Service exposes only `http` (8080), Prometheus scrapes pods directly via the
  `prometheus.io/*` annotations, and `k8s/network-policy.yaml` admits 8081 only from the
  `monitoring` namespace.
- The scrape exemption keys off the socket's local port (`ManagementPortScrapeMatcher`), not the
  `Host` header, so it can't be reached by spoofing headers through the public port.
- If Grafana panels are empty: check `curl http://<app-host>:8081/actuator/prometheus` from the
  Prometheus host (expect 200), then the target state at Prometheus → Status → Targets. A 401 there
  means the request isn't hitting the management port.

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
`DELETE /api/candidate/account` cascades all DB rows and purges the resume object from object storage (RustFS). To erase
on request from ops, delete the `users` row (FKs cascade) and call `FileStorageService.delete(key)` for
the stored `resume_file_key`.
As of `V6__consents_and_erasure.sql`, `notification_logs` cascades too (it used to block deleting a
user who had received an alert). The user's `consents` ledger is erased with the account.

## Data export (DPDP right to access)
Candidates download their data from the dashboard (`GET /api/candidate/account/export`, JSON:
account, profile, applications, saved jobs, alert settings, alerts received, consent history). For a
request that arrives by email or phone, verify the requester owns the number, then produce the same
document with `AccountService.export(userId)` from an ops shell.

## Consent ledger (DPDP notice and consent)
- Table `consents` is append-only; the newest row per `(user_id, purpose)` is the current state.
- `WHATSAPP_ALERTS` withdrawn → the notification worker skips the user (counted as processed, no DLQ).
- `RESUME_PROCESSING` is recorded automatically when a candidate uploads a resume or starts the
  WhatsApp chat (`notice_version` says which notice they saw).
- Audit a user: `SELECT purpose, granted, notice_version, created_at FROM consents WHERE user_id = ? ORDER BY created_at DESC;`

## Moderation queue (ADR 0007)
Admins are the phones in `NAUKRI_ADMIN_PHONES`; they get `ROLE_ADMIN` at their next OTP login and use
`/admin/moderation` in the web app (`/api/admin/moderation/**`).
- Queue size: `SELECT count(*) FROM jobs WHERE moderation_status = 'PENDING_REVIEW';` — alert if the
  oldest pending job is older than a working day.
- Tuning: `TRUST_REVIEW_THRESHOLD` (default 50) and `TRUST_REPORT_THRESHOLD` (default 3). Phrase lists
  are data files in `src/main/resources/trust/` (payment and get-rich-quick phrases in English,
  Hinglish and Devanagari); add a phrase there, not in code.
- Every decision goes through the outbox, so an approval becomes searchable within the sync interval
  and releases the candidate alerts that were held back. A rejected job stays in Postgres (for the
  employer and audit) but is never indexed.
- Bulk re-check after changing rules: editing a job re-scores it; there is no batch re-score job.

## IVR (voice apply, ADR 0008)
Point the Twilio phone number's **Voice** webhook (HTTP POST) at
`https://<public-host>/api/webhooks/twilio/voice`. The gather steps are relative URLs under
`/api/webhooks/twilio/voice/gather/{menu|job|confirm}`. With `TWILIO_PROVIDER=twilio` and
`TWILIO_AUTH_TOKEN` set, every request must carry a valid `X-Twilio-Signature`; behind a proxy, make
sure the public scheme/host reach the app (forwarded headers), or signatures will not match.
Call state is in Redis (`ivr:call:{CallSid}`, 15 min TTL).

## Parse bulkhead and LLM tracing
- `RESUME_PARSE_CONCURRENCY` (default 4) caps concurrent resume parses (`@ConcurrencyLimit`); extra
  uploads wait in `PENDING`. Raise it with LLM quota, not CPU.
- `LLM_TRACER=langfuse` with `LANGFUSE_HOST`/`LANGFUSE_PUBLIC_KEY`/`LANGFUSE_SECRET_KEY` sends one
  trace per parse (metadata only: provider, model, prompt version, latency, JSON validity, sizes).
  `LLM_TRACER=log` writes the same as a log line.
