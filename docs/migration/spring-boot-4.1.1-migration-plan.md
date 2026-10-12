# Spring Boot 4.0.6 → 4.1.1 Migration Plan

**Branch:** `chore/spring-boot-4.1.1` · **Java:** 21 (unchanged) · **Gradle:** 9.5.1 (unchanged)
**Goal:** move the backend from Spring Boot 4.0.6 to 4.1.1 with zero functional regressions, keep the
Next.js 16 frontend, the eval gate and CI green, and bring the Claude skills/rules in line with 4.1.1.

---

## 1. Verified facts (checked, not assumed)

| Fact | Source / how verified |
|---|---|
| Spring Boot 4.1.1 is GA (2026-08-20); 4.1.0 GA was 2026-06-10; 4.1.x OSS support to 2027-07 | spring.io blog, release notes |
| Java 21 stays supported; Gradle 9.5.1 + Gradle Kotlin DSL unchanged | 4.1 release notes (no new Java/Gradle minimum except jOOQ, which we don't use) |
| **4.1 removes every class/method/property deprecated in 4.0** | 4.1 release notes, "Upgrading" |
| Our code has **zero** deprecation/removal warnings on 4.0.6 | `compileJava compileTestJava` with `-Xlint:deprecation -Xlint:removal` |
| None of our `application.yml` keys are removed/renamed in 4.1 | 4.1.0 Configuration Changelog (removed keys are `logging.file.*`, `spring.data.redis.lettuce.cluster.refresh.adaptive`, Oracle UCP — none used) |
| `io.spring.dependency-management` 1.1.7 is still the latest | Maven Central metadata |
| `resilience4j-spring-boot4` 2.4.0 is still the latest (not in any BOM → must stay pinned) | Maven Central metadata; no known 4.1 incompatibility reported |
| Elasticsearch image `9.4.5` exists (published 2026-08-11) | Docker Hub tag API |

### Managed-dependency delta (from the two `spring-boot-dependencies` BOMs)

| Dependency | 4.0.6 | 4.1.1 | Impact on NaukriNearby |
|---|---|---|---|
| Spring Framework | 7.0.7 | 7.0.9 | patch — none expected |
| Spring Security | 7.0.5 | **7.1.1** | minor — re-verify JWT resource server, `@PreAuthorize`, filter order |
| Spring Data BOM | 2025.1.5 | **2026.0.1** | minor — re-verify JPA native queries, ES repositories/`ElasticsearchClient` |
| Hibernate ORM | 7.2.12 | **7.4.5** | minor — re-verify `hibernate-spatial` (PostGIS `Point`) + `@JdbcTypeCode` vector mapping |
| **Elasticsearch Java client** | 9.2.8 | **9.4.5** | **server image must move to 9.4.5** (Compose + Testcontainers) — project rule: server major/minor matches the bundled client |
| Lettuce | 6.8.2 | **7.5.2** | **major** — re-verify Redis Streams (notification pipeline), rate limiter, OTP/refresh storage |
| Flyway | 11.14.1 | **12.4.0** | **major** — re-verify V1–V4 migrations + `baseline-on-migrate` on PostGIS/pgvector image |
| Jackson 3 | 3.1.2 | 3.1.5 | patch |
| Micrometer / Tracing | 1.16.5 / 1.6.5 | 1.17.1 / 1.7.1 | minor — re-verify `/actuator/prometheus` custom metrics |
| Tomcat | 11.0.21 | 11.0.24 | patch (note: temp basedir now deleted on shutdown — we don't rely on it) |
| Testcontainers | 2.0.5 | 2.0.5 | none |
| JUnit / Lombok | 6.0.3 / 1.18.46 | 6.0.3 / 1.18.46 | none |
| PostgreSQL JDBC | 42.7.10 | 42.7.13 | patch |

### 4.1 behavioural changes reviewed against this codebase

| 4.1 change | Applies to us? |
|---|---|
| Constructor-bound `Optional` params bind to `Optional.empty()` instead of `null` | No `Optional` in our `@ConfigurationProperties` records — verify with grep |
| Empty YAML objects retained in `PropertySource` | No empty maps in `application.yml` |
| `spring.data.jpa.repositories.bootstrap-mode` deferred/lazy changes | Not set (default) |
| Tomcat temp basedir deleted on close | Multipart uploads are streamed to MinIO; no reliance |
| `layertools` jar mode removed | Dockerfile uses plain `java -jar` — no impact |
| Gradle `bootBuildInfo` now writes `META-INF/build-info.properties` | `bootBuildInfo` not configured |
| `spring-boot-starter-data-redis` now depends on `spring-messaging` | additive only |
| Devtools LiveReload deprecated | not configured |

**Risk ranking:** (1) ES client/server alignment, (2) Lettuce 7 under Redis Streams, (3) Flyway 12 on
the PostGIS image, (4) Hibernate 7.4 spatial/vector mapping, (5) resilience4j 2.4.0 built against 4.0,
(6) Security 7.1 filter chain. Everything else is patch-level.

---

## 2. Execution environment

Docker is not on the Windows PATH; Docker 29.9 runs inside WSL `Ubuntu-D`. Windows also has a native
PostgreSQL 17 on `localhost:5432`, which would shadow the Compose Postgres. Therefore:

- **Backend build + Testcontainers tests:** run inside an `eclipse-temurin:21-jdk` container in WSL
  with the Docker socket mounted and `--network host`, building a *copy* of the sources (so the
  Windows `build/` and `.gradle/` dirs are untouched).
- **Runtime stack:** `docker compose up` (Postgres+PostGIS+pgvector, Redis, ES, MinIO) in WSL; the
  backend jar runs in a `eclipse-temurin:21-jre` container with `--network host`, so it reaches the
  WSL Postgres, and WSL's localhost forwarding exposes `:8080` to Windows.
- **Frontend:** Node 24 on Windows (`next build && next start`) on `:3000`, `NEXT_PUBLIC_API_URL=http://localhost:8080`.
- **Browser E2E:** the Claude desktop built-in browser against `http://localhost:3000`.

---

## 3. Phased plan

### Phase 0 — Baseline on 4.0.6 (before touching anything)
1. Run `./gradlew build` (compile + all tests, Testcontainers) on the unchanged 4.0.6 tree; record
   pass/fail per test class. Any pre-existing failure is documented, not attributed to the upgrade.
2. Record the dependency tree (`dependencies --configuration runtimeClasspath`) for later diffing.

**Phase 0 outcome (2026-10-12).** The first baseline run failed 12 of 18 tests, all with one root
cause outside Spring Boot: **two upstream container images had broken since the last green CI run.**
Both were fixed before any Boot change, and the baseline then passed **18/18 on 4.0.6**.

| Pre-existing break | Cause | Fix |
|---|---|---|
| `docker/postgres/Dockerfile` fails at `apt-get install postgresql-16-pgvector` (exit 100) | `postgis/postgis:16-3.4` and `16-3.5` are frozen on Debian bullseye, and `apt.postgresql.org` now returns 404 for `bullseye-pgdg` | Base switched to `pgvector/pgvector:0.8.7-pg16-bookworm` + `postgresql-16-postgis-3` from PGDG (PG 16.15, PostGIS 3.6.4, pgvector 0.8.7). Verified `CREATE EXTENSION postgis/vector`, `ST_DWithin` and `<=>` |
| `minio/minio:latest` pull → "repository does not exist" | MinIO removed its community images from Docker Hub (Sept 2026), and the quay.io copy is frozen behind auth | Replaced by **RustFS 1.0.1** after a head-to-head against SeaweedFS/Garage/Ceph on the app's real storage path ([ADR 0006](../adr/0006-rustfs-object-storage.md)); new `ObjectStorageIntegrationTests` gate the S3 contract |

These would have broken CI on the next push regardless of the upgrade.

### Phase 1 — Build bump
1. `build.gradle.kts`: `id("org.springframework.boot") version "4.1.1"`.
2. Keep `io.spring.dependency-management` 1.1.7 (latest) and `resilience4j-spring-boot4:2.4.0` (latest).
3. Do **not** opportunistically bump unrelated majors (MinIO 8→9, pgvector, JTS) — out of scope;
   PDFBox stays on 3.0.7 for the same reason.
4. Diff the runtime classpath against Phase 0 to confirm only the expected managed versions moved.

### Phase 2 — Elasticsearch server alignment (9.2.8 → 9.4.5)
1. `compose.yaml` → `docker.elastic.co/elasticsearch/elasticsearch:9.4.5`.
2. `TestcontainersConfiguration` → same image.
3. README version notes (root + backend).

### Phase 3 — Static checks on 4.1.1
1. Recompile main + test with `-Xlint:deprecation -Xlint:removal` → must be warning-free (catches
   anything newly deprecated in 4.1 so the 4.2 upgrade is also clean).
2. Temporarily add `spring-boot-properties-migrator` (runtimeOnly) and boot the app once: it logs any
   renamed/removed property. Remove it afterwards.
3. Grep `@ConfigurationProperties` records for `Optional` params (4.1 binding change).

### Phase 4 — Automated test suite on 4.1.1
`./gradlew build` with Testcontainers: `NaukriNearbyApplicationTests`, `BackendIntegrationTests`,
`ApiEndpointsIntegrationTests`, `MinioAndWhatsAppIntegrationTests`, `SearchServiceFallbackTest`,
`TwilioSignatureValidatorTest`, `StubLlmExtractionProviderTest`, `ResumeExtractionServiceTest`.
Pass bar: **same or better than the Phase 0 baseline, zero new failures.**

### Phase 5 — Runtime smoke test (real stack)
1. `docker compose up -d` (postgres, redis, elasticsearch, minio) and wait for health.
2. Boot the 4.1.1 jar; confirm Flyway applied V1–V4 cleanly on Flyway 12, the ES index bootstrap
   ran, and the startup log has no WARN about deprecated/unknown properties.
3. Probe: `/actuator/health` (+ liveness/readiness groups), `/actuator/prometheus` (custom metrics),
   `/actuator/circuitbreakers` + `/actuator/retries` (proves resilience4j 2.4.0 works on 4.1).
4. API walk-through with curl: send-otp → verify-otp (JWT RS256) → `/api/auth/me` → create job
   (employer) → outbox drains to ES → search (ES path) → search with ES stopped (PostGIS fallback) →
   apply → saved jobs → refresh-token rotation → rate-limit 429 → `ProblemDetail` 4xx shapes.

### Phase 6 — Browser E2E (frontend ↔ 4.1.1 backend)
Build and start the Next.js 16 app, then drive it in the built-in browser:
home page → job search (geo radius, list + map) → job detail (`/jobs/[slug]`, JSON-LD) → login with
phone + OTP → candidate resume upload (`/candidate/resume`, MinIO + stub extraction). Check the
console and network panels for errors, CORS failures and non-2xx API calls.

### Phase 7 — Eval gate
Run `eval/run_eval.py --ci` against the running stub-backed app (the same gate CI runs), so the
resume-extraction/matching/translation scores stay at or above `eval/baseline.json`.

### Phase 8 — Docs, rules and Claude skills
- `AGENTS.md` stack mandate → 4.1.1.
- `.claude/skills/SKILL.md` (spring-boot skill) + `security-and-api.md`: version bump, a short
  "4.1 upgrade rules" section, and fixes for drifted snippets that would generate broken code today
  (non-existent `spring-boot-starter-aop` / `spring-boot-starter-testcontainers`, TC1-style
  `PostgreSQLContainer<?>` + `org.testcontainers:postgresql` coordinates, ES **8.13** test image,
  unpinned resilience4j, `@Autowired RestTestClient` without `@AutoConfigureRestTestClient`).
- `naukrinearby-java21` skill: Boot version references.
- Re-package `naukrinearby-spring-boot.skill` and `naukrinearby-java21.skill`.
- README (root + backend), master plan version mentions, ADR 0005 recording the decision.
- `naukrinearby/docs/future-work/README.md`: remove the stale "resilience4j-spring-boot4 does not
  exist yet" note.

### Phase 9 — Rollback
The upgrade is two files of build config plus an image tag. Rollback = revert the branch (or set the
plugin back to `4.0.6` and ES back to `9.2.8`). No schema migration is added, so the database needs
no rollback; ES indices created by 9.4.5 cannot be opened by 9.2.8, so on rollback drop the local
`es_data` volume and let the outbox reconcile/reindex (alias-flip runbook, ADR 0003).

---

## 4. Acceptance criteria — results (2026-10-12)
- [x] `./gradlew build` green on 4.1.1: **23/23** (18 baseline tests, no regressions, plus 5 new).
- [x] Main + test sources compile with **zero** deprecation/removal warnings on 4.1.1.
- [x] App boots against the real stack (PG 16.15 + PostGIS 3.6 + pgvector 0.8.7, ES 9.4.5, Redis 7,
      RustFS 1.0.1). Flyway 12 applied V1–V4, ES index + alias bootstrapped, and the only WARN is the
      expected dev-mode ephemeral JWT key.
- [x] API walkthrough **26/26**: OTP→RS256 JWT, `/me`, 401/403/404/409/429 + `ProblemDetail` shapes,
      job create → outbox → ES search, **ES stopped → PostGIS fallback**, apply idempotency, saved jobs,
      refresh rotation, `/actuator/prometheus` custom metrics, `/actuator/circuitbreakers` and `/retries`
      (resilience4j 2.4.0 works on 4.1.1).
- [x] Browser E2E (Next.js 16 production build ↔ 4.1.1): home → login (OTP) → job search → SSR job
      detail with `JobPosting` JSON-LD → save → apply → résumé upload to RustFS → parse `COMPLETED` →
      profile auto-filled. No console errors and no failed API calls.
- [x] Eval gate passes vs `eval/baseline.json` (skill F1 1.0, city acc 1.0, exp MAE 0).
- [x] Skills, AGENTS.md, READMEs, master plan and ADRs reference 4.1.1 consistently; skill archives re-packaged.
- [x] Runtime dependency diff matches §1 exactly (only Boot-managed versions moved; `spring-messaging`
      added, as the 4.1 notes say).

## 5. Bugs found during verification

The same API walkthrough was run against the **4.0.6 jar** on the same stack to separate regressions
from pre-existing bugs. **The upgrade introduced no regressions.** It found these pre-existing issues:

| # | Issue | Pre-existing on 4.0.6? | Status |
|---|---|---|---|
| 1 | `postgis/postgis:16-3.x` base on EOL Debian bullseye → pgvector install 404s | yes (upstream) | **Fixed**: `pgvector/pgvector:0.8.7-pg16-bookworm` + PGDG PostGIS |
| 2 | `minio/minio` image deleted from Docker Hub | yes (upstream) | **Fixed**: RustFS 1.0.1 (ADR 0006) + `ObjectStorageIntegrationTests` |
| 3 | `GET /api/auth/me` without a token → **500** (`/api/auth/**` was `permitAll`, null principal) | yes | **Fixed**: only send-otp/verify-otp/refresh/logout are public; test added |
| 4 | `@PreAuthorize` denial (e.g. candidate `POST /api/jobs`) → **500** (catch-all handler swallowed `AuthorizationDeniedException`) | yes | **Fixed**: `AccessDeniedException` → 403 `ProblemDetail`; test added |
| 5 | `monitoring/prometheus.yml` scrapes `/actuator/prometheus`, but `/actuator/**` requires `ROLE_ADMIN` and nothing issues admin tokens, so Grafana panels stay empty | yes | **Open**: needs a decision (public metrics on a separate management port, or scrape auth) |
| 6 | Spring Data WARN "Serializing PageImpl instances as-is is not supported" on paged endpoints | yes | **Fixed (follow-up PR)**: controllers return a `PageResponse<T>` record that keeps the documented flat contract, and a test asserts the shape and that the warning is gone |
