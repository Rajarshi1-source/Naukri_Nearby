# NaukriNearby — Backend MVP

Hyperlocal job board backend on **Spring Boot 4.1.1 / Java 21 / Gradle**. Implements phone+OTP→JWT
auth, geotagged jobs with a transactional PostgreSQL→Elasticsearch outbox, real-LLM resume parsing
wired to a Python eval harness, Elasticsearch geo-search with a PostGIS fallback, pgvector matching,
and an idempotent WhatsApp alert pipeline (Twilio/Bhashini stubbed). The Bharat differentiators
(ADRs 0007–0009) add fraud scoring and moderation, employer trust levels, voice (IVR) apply,
Hinglish search, skill-gap suggestions, and a DPDP consent ledger with data export.

## Architecture (write path = signature pattern)
- **Writes** commit the aggregate **and** an `outbox_events` row in one transaction. `OutboxSyncWorker`
  drains the outbox into Elasticsearch with an idempotent, version-guarded upsert on `job_id`.
  We never dual-write to PG and ES.
- **Reads**: search hits Elasticsearch (geo_distance + multi_match, Redis-cached); if ES is down it
  degrades to a PostGIS `ST_DWithin` query (`fellBackToPostgis=true`).
- **Notifications**: a new job → matched candidates (pgvector + PostGIS) → Redis Stream → consumer
  that dedups on `(user, job)` via SETNX **and** a unique DB row.

## Datastores
- PostgreSQL 16 + **PostGIS** + **pgvector** (source of truth) — built from `docker/postgres/Dockerfile`.
- Elasticsearch 9.4 (derived search index), Redis 7 (cache/streams/idempotency), RustFS (S3-compatible resume storage).

## Run locally
```bash
# 1. Infra
docker compose up -d          # postgres(+postgis+pgvector), elasticsearch, redis, rustfs

# 2. Config — copy and fill in (real LLM key for meaningful extraction)
cp .env.example .env          # set LLM_API_KEY; defaults match compose

# 3. App
./gradlew bootRun
```
Defaults run fully offline: OTP/Twilio/Bhashini are stubbed and embeddings use a deterministic local
model. Set `EXTRACTION_PROVIDER=gpt5mini` + `LLM_API_KEY` for real resume extraction (or `=stub` offline).

## Key endpoints
- `POST /api/auth/send-otp`, `POST /api/auth/verify-otp`, `POST /api/auth/refresh`, `GET /api/auth/me`
- `POST /api/jobs` (employer), `GET /api/jobs/search`, `GET /api/jobs/{slug}`, `GET /api/employer/jobs`
- `POST /api/resumes/upload` (candidate, 202), `GET /api/resumes/{id}/parse-status`
- `POST /api/jobs/{id}/apply`, `GET /api/candidate/recommendations`, `.../notification-preferences`
- `POST /api/webhooks/twilio` (signature-verified); actuator on the internal management port 8081 (`/actuator/prometheus` scrape, health probes)
- Trust and moderation: `POST /api/jobs/{id}/report`, `GET/PUT /api/employer/profile` (GSTIN),
  `GET /api/admin/moderation/jobs`, `POST /api/admin/moderation/jobs/{id}/decision` (`ROLE_ADMIN`)
- Candidate: `GET /api/candidate/jobs/{id}/skill-gap`, `GET/PUT /api/candidate/consents`,
  `GET /api/candidate/account/export`, `DELETE /api/candidate/account`; `PUT /api/auth/me/language`
- Voice apply (Twilio Voice, signature-verified): `POST /api/webhooks/twilio/voice`,
  `POST /api/webhooks/twilio/voice/gather/{menu|job|confirm}`
- WhatsApp-first apply: `POST /api/webhooks/twilio/inbound`

The full contract is in `../.claude/skills/security-and-api.md` (Part B).

## Differentiator configuration
| Env | Default | Effect |
| --- | --- | --- |
| `NAUKRI_ADMIN_PHONES` | empty | Phones that get `ROLE_ADMIN` at OTP login (moderation queue) |
| `TRUST_REVIEW_THRESHOLD` | 50 | Risk score at which a job waits for review |
| `TRUST_REPORT_THRESHOLD` | 3 | User reports that send a job back to review |
| `GST_VERIFIER` | `checksum` | Offline GSTIN check digit, or `none` |
| `RESUME_PARSE_CONCURRENCY` | 4 | `@ConcurrencyLimit` bulkhead on resume parsing |
| `LLM_TRACER` | `none` | `log` or `langfuse` (metadata only) |

## Tests
```bash
./gradlew test     # Testcontainers spins PostGIS+pgvector, Elasticsearch 9.4.5, Redis, RustFS (needs a Docker daemon)
```

## Resume-extraction eval
See [`eval/README.md`](eval/README.md). With the app running and `EVAL_ENABLED=true`:
```bash
cd eval && pip install -r requirements.txt && python run_eval.py --ci
```

## Conventions
Binding project rules live in [`../AGENTS.md`](../AGENTS.md) and `../.claude/skills/`.
