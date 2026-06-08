# NaukriNearby — Backend MVP

Hyperlocal job board backend on **Spring Boot 4.0.6 / Java 21 / Gradle**. Implements phone+OTP→JWT
auth, geotagged jobs with a transactional PostgreSQL→Elasticsearch outbox, real-LLM resume parsing
wired to a Python eval harness, Elasticsearch geo-search with a PostGIS fallback, pgvector matching,
and an idempotent WhatsApp alert pipeline (Twilio/Bhashini stubbed).

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
- Elasticsearch 8.13 (derived search index), Redis 7 (cache/streams/idempotency), MinIO (resumes).

## Run locally
```bash
# 1. Infra
docker compose up -d          # postgres(+postgis+pgvector), elasticsearch, redis, minio

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
- `POST /api/webhooks/twilio` (signature-verified), `GET /actuator/prometheus`

## Tests
```bash
./gradlew test     # Testcontainers spins PostGIS+pgvector, ES8, Redis (needs a Docker daemon)
```

## Resume-extraction eval
See [`eval/README.md`](eval/README.md). With the app running and `EVAL_ENABLED=true`:
```bash
cd eval && pip install -r requirements.txt && python run_eval.py --ci
```

## Conventions
Binding project rules live in [`../AGENTS.md`](../AGENTS.md) and `../.claude/skills/`.
