# NaukriNearby — Global Project Rules

These rules are binding for ALL work in this repository. They mirror the mandates in
`.claude/skills/SKILL.md` and `.claude/skills/security-and-api.md` so they apply globally,
even when the skill is not auto-triggered. The skills remain the authoritative source of detail.

## Stack mandate
- Backend: Spring Boot 4.1.1 (Spring Framework 7.0.9, Spring Security 7.1, Hibernate 7.4),
  Java 21 (LTS), Gradle 9.5.1 (Kotlin DSL). Do NOT generate Spring Boot 3.x or Java 25 code, and do
  NOT use APIs deprecated in Boot 4.0 (removed in 4.1). Code must compile clean under
  `-Xlint:deprecation -Xlint:removal`.
- Dependency versions: let the Boot BOM manage everything it covers. Pin only non-BOM libraries
  (`resilience4j-spring-boot4:2.4.0`, MinIO Java SDK, pgvector, JTS, PDFBox).
- Object storage is RustFS 1.0.1 (S3-compatible), accessed only through `FileStorageService` + the
  MinIO Java SDK. Never call vendor-specific admin APIs; keep the code portable to any S3 store.
- Elasticsearch server image must equal the Boot-managed ES Java client (**9.4.5** on Boot 4.1.1)
  in both `compose.yaml` and `TestcontainersConfiguration`. Bump them together on every Boot upgrade.
- Frontend: Next.js 16 (App Router), React 19.2, TypeScript strict, on Node.js 24 LTS.
  Tailwind v4 + shadcn/ui (components copied into the repo). Do NOT generate Next.js 14/15-era
  code, Pages Router, or synchronous `params`/`searchParams`/`cookies()`/`headers()` access — those
  request APIs are async Promises in Next.js 16 and must be awaited. Turbopack is the default
  bundler (no `--turbopack` flag); route gating uses `proxy.ts`, not `middleware.ts`.
- Datastores: PostgreSQL 16 + PostGIS + pgvector (source of truth, CP), Elasticsearch 9.4
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
- Actuator is on the internal management port 8081. Only health/info and the Prometheus scrape (8081 only,
  via `ManagementPortScrapeMatcher`) are anonymous; all other actuator endpoints need `ROLE_ADMIN`.
- Resumes are PII (DPDP Act): encrypt at rest, signed-URL access, redact PII in logs.

## Consistency & resilience
- CP writes (Postgres) / AP reads (Elasticsearch); read-your-writes for the job poster from PG.
- Idempotency: WhatsApp dedup on `(user_id, job_id)`; ES upsert on `job_id`.
- Graceful degradation: ES down -> PostGIS `ST_DWithin` fallback; translation down -> send English.

## Testing
- Integration tests use Testcontainers (`@ServiceConnection`) + `RestTestClient`.
