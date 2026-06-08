# NaukriNearby — Global Project Rules

These rules are binding for ALL work in this repository. They mirror the mandates in
`.claude/skills/SKILL.md` and `.claude/skills/security-and-api.md` so they apply globally,
even when the skill is not auto-triggered. The skills remain the authoritative source of detail.

## Stack mandate
- Backend: Spring Boot 4.0.6 (Spring Framework 7), Java 21 (LTS), Gradle (Kotlin DSL).
  Do NOT generate Spring Boot 3.x or Java 25 code.
- Datastores: PostgreSQL 16 + PostGIS + pgvector (source of truth, CP), Elasticsearch 8.x
  (derived search index, AP), Redis 7 (cache + Streams + idempotency).
- Backend module lives in `naukrinearby/`; base package is `com.naukrinearby`.

## Architecture rules (never violate)
- Constructor injection only (`@RequiredArgsConstructor`); never field `@Autowired`.
- `@Transactional` on public service methods only — never controllers or private methods.
- Controllers do HTTP only: `@Valid` input, map to/from records, delegate to a service.
- DTOs are records; entities are mutable classes; map via static factories (`X.from(...)`).
- NEVER dual-write to Postgres and Elasticsearch. Writes commit the aggregate + an outbox
  event in one transaction; the `OutboxSyncWorker` syncs to ES with an idempotent upsert on
  `job_id` (version-guarded). This is the project's signature pattern.
- Errors return `ProblemDetail` (RFC 9457) via a single `@RestControllerAdvice`.
- No hardcoded secrets; bind all keys/URLs via `@ConfigurationProperties` from env vars.
- External providers (LLM, embedding, translation, Twilio) sit behind provider-agnostic
  adapter interfaces selected by config. Twilio/Bhashini ship as stubs in this MVP.

## Security mandate (see security-and-api.md)
- Auth: phone + OTP -> JWT (RS256), stateless sessions; refresh tokens stored in Redis by `jti`.
- Rate limit OTP (5/hr) and APIs via Redis; verify Twilio webhook signatures; dedup on `MessageSid`.
- Resumes are PII (DPDP Act): encrypt at rest, signed-URL access, redact PII in logs.

## Consistency & resilience
- CP writes (Postgres) / AP reads (Elasticsearch); read-your-writes for the job poster from PG.
- Idempotency: WhatsApp dedup on `(user_id, job_id)`; ES upsert on `job_id`.
- Graceful degradation: ES down -> PostGIS `ST_DWithin` fallback; translation down -> send English.

## Testing
- Integration tests use Testcontainers (`@ServiceConnection`) + `RestTestClient`.
