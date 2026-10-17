---
name: naukrinearby-spring-boot
description: >-
  Build and maintain the NaukriNearby backend on Spring Boot 4.1.1 (Spring Framework 7) with
  Java 21 and Gradle. Use this skill for ANY backend work — REST controllers, services,
  repositories, the transactional outbox + Elasticsearch sync, the provider-agnostic
  LLM/embedding/translation/Twilio adapters, Redis Streams pipelines, pgvector/PostGIS queries,
  Spring Security (phone+OTP, JWT), resilience (circuit breakers, retry, timeouts, bulkheads),
  config, error handling, or tests — even when the version isn't named. Trigger on Spring Boot,
  Spring Framework 7, @HttpExchange / HTTP Service Clients, native @Retryable / @ConcurrencyLimit,
  Resilience4j, ProblemDetail, @Transactional, Spring Data JPA, Testcontainers, RestTestClient,
  Actuator/Micrometer/OpenTelemetry, virtual threads, or Gradle. Also trigger on the Bharat
  differentiators: fake-job/fraud risk scoring, moderation queue, job reports, trust levels and
  GSTIN verification, IVR/voice apply (Twilio Voice TwiML), WhatsApp-first apply, Hinglish query
  normalization, skill-gap recommendations, DPDP consent ledger and data export, Langfuse
  LlmTracer, and the @ConcurrencyLimit parse bulkhead. MANDATE: Spring Boot 4.1.1 +
  Java 21 + Gradle — never generate 3.x code, APIs deprecated in 4.0 (removed in 4.1), or Java 25
  code. Pair with naukrinearby-java21 for language concerns.
---

# Spring Boot 4.1.1 (Framework 7) — NaukriNearby Backend

You are a senior Spring engineer building NaukriNearby on **Spring Boot 4.1.1 / Spring Framework 7,
Java 21, Gradle**. The backend is a layered REST application that orchestrates many external
services and keeps a PostgreSQL (PostGIS + pgvector) source-of-truth consistent with an
Elasticsearch search index via the **transactional outbox** pattern.

## Why this version (state it confidently if asked)

- **Spring Boot 4.1.1 runs on Java 21** (4.1 keeps the Java 17+ baseline). Keep Java 21 LTS — the
  whole project, Dockerfile (`eclipse-temurin:21`), and CI already target it. No Java 25 needed.
- **4.1 is the current supported minor:** 4.1.0 went GA on 2026-06-10, and 4.1.1 (2026-08-20) is
  its first patch, with 98 fixes. The project started on 4.0.6 and was migrated to 4.1.1 with
  no code changes (see `docs/migration/spring-boot-4.1.1-migration-plan.md`, ADR 0005). 3.x is
  end-of-life.
- **Framework 7 features map onto this project:** HTTP Service Clients for the adapters, native
  `@Retryable`/`@ConcurrencyLimit` for resilience, the OpenTelemetry starter for observability,
  module-focused starters, and JSpecify null-safety.

## Spring Boot 4.1 rules (keep the build upgrade-clean)

- **No deprecated Boot/Framework API.** 4.1 *removed* everything deprecated in 4.0, and 4.2 will do
  the same to 4.1 deprecations. Main and test code must compile warning-free with
  `-Xlint:deprecation -Xlint:removal`; if an API is deprecated, use its documented replacement.
- **Elasticsearch server = bundled client version.** Boot 4.1.1 manages the ES Java client
  **9.4.5**, so Compose and Testcontainers use `docker.elastic.co/elasticsearch/elasticsearch:9.4.5`.
  Never point the app at an 8.x cluster, and bump the image whenever a Boot upgrade moves the client.
- **Managed versions:** don't pin anything the Boot BOM manages (Hibernate 7.4, Spring Security 7.1,
  Spring Data 2026.0, Lettuce 7, Flyway 12, Jackson 3.1, Micrometer 1.17, Testcontainers 2). Pin
  only libraries outside the BOM, such as `resilience4j-spring-boot4:2.4.0`, the MinIO Java SDK (S3 client), pgvector, JTS
  and PDFBox.
- **Jackson 3:** inject `tools.jackson.databind.ObjectMapper`/`JsonMapper`. Annotations stay in
  `com.fasterxml.jackson.annotation`. Don't add the deprecated Jackson 2 bridge.
- **Config binding:** in 4.1, constructor-bound `Optional<…>` parameters bind to `Optional.empty()`
  (not `null`), and empty YAML maps are kept. Prefer plain nullable/defaulted record components.
- **Lettuce 7 / Flyway 12** are the drivers under Redis Streams and migrations. Use the Spring
  abstractions (`StringRedisTemplate`, `StreamOperations`, Flyway auto-config) rather than raw
  driver APIs, so future driver majors stay transparent.

## Critical rules (never violate)

- **Constructor injection only** (via Lombok `@RequiredArgsConstructor` or explicit constructors).
  Never field injection (`@Autowired` on fields).
- **`@Transactional` on service methods only** — never on controllers, never on private methods
  (Spring proxies can't intercept them).
- **Controllers do HTTP only**: validate input (`@Valid`), map to/from records, delegate to a
  service. No business logic, no repositories, no external calls in controllers.
- **DTOs are records; entities are classes.** Map with static factories (`JobResponse.from(job)`).
- **Never dual-write to Postgres and Elasticsearch.** Writes go to Postgres + an outbox event in
  one transaction; a worker syncs to ES (see Outbox below). This is the project's signature pattern.
- **No hardcoded secrets.** All keys/URLs via `@ConfigurationProperties` bound from env vars.
- **Errors return `ProblemDetail`** (RFC 9457) through a single `@RestControllerAdvice`.

## Layered architecture

```
@RestController   → HTTP concerns: @Valid, request/response records, status codes
       ↓
@Service          → business logic, @Transactional boundaries, orchestration
       ↓
@Repository       → Spring Data JPA + native PostGIS/pgvector queries
       ↓
@Entity           → JPA mappings (mutable classes)
```

Package layout follows §7.1: `config`, `controller`, `service`, `worker`, `repository`,
`model.{entity,dto,enums,elasticsearch}`, `exception`, `util`.

## Gradle build (Kotlin DSL)

```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")  // Boot 4 modular starter
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server") // RS256 JWT
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.hibernate.orm:hibernate-spatial")                 // PostGIS geometry
    runtimeOnly("org.postgresql:postgresql")

    // Resilience4j circuit breakers + retry metrics. Not in any BOM → pin it. AOP via spring-aspects
    // (there is NO spring-boot-starter-aop in Boot 4; the AspectJ starter is spring-boot-starter-aspectj).
    implementation("io.github.resilience4j:resilience4j-spring-boot4:2.4.0")
    implementation("org.springframework:spring-aspects")

    // Observability
    implementation("org.springframework.boot:spring-boot-micrometer-tracing-brave")
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Tests: one -test starter per technology + Testcontainers 2 (artifacts are testcontainers-<module>)
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-elasticsearch")
}
```

> Boot 4 modularized the starters. Prefer the specific `spring-boot-starter-<technology>` (for
> example `spring-boot-starter-webmvc`, not the old umbrella `web` artifact) and the matching
> `-test` starters. `naukrinearby/build.gradle.kts` is the source of truth, so copy coordinates from
> there. Gradle 9.5.1 (wrapper) is supported.

## Spring Boot 4 / Framework 7 features — used HERE

### HTTP Service Clients (`@HttpExchange`) for the adapters (§2)

The project puts every external model/provider behind an adapter. Framework 7's declarative HTTP
clients are the cleanest way to implement those — define an interface, get a proxy, no
RestTemplate/WebClient boilerplate:

```java
@HttpExchange(url = "/v1", accept = "application/json")
public interface BhashiniClient {
    @PostExchange("/translate")
    TranslationResponse translate(@RequestBody TranslationRequest req);
}

@Configuration
class HttpClientsConfig {
    @Bean
    BhashiniClient bhashiniClient(RestClient.Builder builder, TranslationProperties props) {
        RestClient client = builder.baseUrl(props.baseUrl())
                .defaultHeader("Authorization", props.apiKey()).build();
        return HttpServiceProxyFactory.builderFor(RestClientAdapter.create(client))
                .build().createClient(BhashiniClient.class);
    }
}
```

The provider-agnostic interface stays as the plan defines it; the HTTP client is the implementation
detail behind each provider (`Gpt5MiniProvider`, `ClaudeHaikuProvider`, etc.):

```java
public interface LlmExtractionProvider {
    <T> T extractStructured(String text, Class<T> schema, String promptVersion);
    String providerId();
}
```

### Native resilience + Resilience4j (§12)

**Project convention:** every external provider method carries Resilience4j **`@Retry(name=…)` +
`@CircuitBreaker(name=…)`**, configured centrally under `resilience4j.*` in `application.yml`
(instances `llm`, `bhashini`, `twilio`, `embedding`, `elasticsearch`). Their state is exposed at
`/actuator/circuitbreakers` and `/actuator/retries`. Follow this pattern for new providers so
breakers, retries and metrics stay uniform.

Framework 7 also ships native `@Retryable` / `@ConcurrencyLimit` (enable them with
`@EnableResilientMethods`). Use `@ConcurrencyLimit` to bulkhead a hot path. **Never stack native
`@Retryable` and R4j `@Retry` on the same method**, because the retries multiply.

```java
@Component
@ConditionalOnProperty(name = "naukri.translation.provider", havingValue = "bhashini")
public class BhashiniTranslationProvider implements TranslationProvider {
    private final RestClient client;              // built in the constructor with props.timeout() (§12)

    @Override
    @Retry(name = "bhashini")                     // io.github.resilience4j.retry.annotation.Retry
    @CircuitBreaker(name = "bhashini")            // io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
    public String translate(String text, String targetLanguageCode) { /* … */ }
}

// TranslationService is the final safety net: on any failure, send English (§9.6).
public String translateOrEnglish(String englishText, String targetLanguageCode) {
    try { return provider.translate(englishText, targetLanguageCode); }
    catch (Exception ex) { return englishText; } // a clear English alert beats a failed send
}
```

Set timeouts per service (LLM 60s, Twilio 10s, ES 3s — §12) on the underlying clients, and open the
breaker at 50% failure over a 20-call window. Use bulkheads / separate worker deployments so
resume-parse load can't starve WhatsApp delivery.

### ProblemDetail error handling (RFC 9457)

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    ProblemDetail handleNotFound(EntityNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation failed");
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage()).toList());
        return pd;
    }

    @ExceptionHandler(ResumeParseException.class)
    ProblemDetail handleParse(ResumeParseException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    // Unmapped URLs throw NoResourceFoundException. Without this they become a catch-all 500.
    @ExceptionHandler(NoResourceFoundException.class)
    ProblemDetail handleNoResource(NoResourceFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "No such endpoint");
    }

    // REQUIRED whenever a catch-all exists: @PreAuthorize denials reach this advice first.
    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(Exception.class)          // last resort → 500, logged
    ProblemDetail handleGeneric(Exception ex) { /* log.error(...) */ return ProblemDetail.forStatus(500); }
}
```

### JSpecify null-safety

Annotate each package with `@NullMarked` (in `package-info.java`) so non-null is the default and
nullable is explicit. This catches NPEs in the adapter and mapping code at build time with the
right tooling.

### API versioning

Framework 7 supports first-class API versioning. If you expose versioned endpoints, prefer the
built-in mechanism over ad-hoc `/v1/` path prefixes scattered across controllers.

## The signature pattern: Transactional Outbox (PG → ES)

Writes commit the aggregate **and** an outbox event in one transaction; a `@Scheduled` worker
drains the outbox into Elasticsearch with an **idempotent upsert keyed on `job_id`**. This is the
project's reliability centerpiece (§9, and the starter code). Do not replace it with dual writes.

```java
@Service
@RequiredArgsConstructor
public class JobService {
    private final JobRepository jobRepo;
    private final OutboxRepository outboxRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public Job createJob(JobCreateRequest req, Long recruiterId) {
        Job job = jobRepo.save(Job.fromRequest(req, recruiterId)); // version starts at 1
        outboxRepo.save(OutboxEvent.builder()
                .aggregateType("job").aggregateId(job.getId())
                .eventType("JOB_CREATED").version(job.getVersion())
                .payload(toJobDocumentJson(job)).status("PENDING").build());
        return job; // both rows commit atomically; ES is NOT touched here
    }
}
```

```java
@Component
@RequiredArgsConstructor
public class OutboxSyncWorker {
    private final OutboxRepository outboxRepo;
    private final ElasticsearchClient es;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void syncBatch() {
        var batch = outboxRepo.fetchPendingBatch(100);   // FOR UPDATE SKIP LOCKED
        for (var event : batch) { /* idempotent upsert on job_id; version guard; retry → DLQ */ }
    }
}
```

Talking points: no lost updates, survives ES downtime (events stay PENDING and drain on recovery),
effectively-once (at-least-once + idempotent upsert), `SKIP LOCKED` lets workers share load, version
guard prevents stale overwrites, nightly reconciliation catches drift.

## Spring Data JPA — PostGIS, pgvector, no N+1

```java
public interface JobRepository extends JpaRepository<Job, Long> {

    @Query(value = """
        SELECT j.*, ST_Distance(j.location, ST_MakePoint(:lng, :lat)::geography)/1000 AS distance_km
        FROM jobs j
        WHERE j.status = 'ACTIVE'
          AND ST_DWithin(j.location, ST_MakePoint(:lng, :lat)::geography, :radiusM)
        ORDER BY distance_km ASC LIMIT :limit
        """, nativeQuery = true)
    List<Job> findWithinRadius(double lat, double lng, double radiusM, int limit);

    // pgvector cosine similarity for matching
    @Query(value = """
        SELECT j.*, 1 - (j.requirement_vector <=> :vec) AS score
        FROM jobs j
        WHERE j.status = 'ACTIVE'
        ORDER BY j.requirement_vector <=> :vec LIMIT :limit
        """, nativeQuery = true)
    List<Job> findSimilar(@Param("vec") float[] vec, int limit);

    @EntityGraph(attributePaths = {"employer"})        // avoid N+1 on list views
    List<Job> findByStatus(JobStatus status);
}
```

Use `@Transactional(readOnly = true)` for read paths, DTO projections for list endpoints, and
`JOIN FETCH`/`@EntityGraph` to kill N+1 — never map lazy associations inside a stream that triggers
per-row queries.

## Configuration (`@ConfigurationProperties`, not scattered `@Value`)

```java
@ConfigurationProperties(prefix = "naukri.llm")
public record LlmProperties(String extractionProvider, String extractionModel,
                            String visionModel, double temperature) {}
```

```yaml
spring:
  threads:
    virtual:
      enabled: true        # virtual threads for the whole app (see naukrinearby-java21)
naukri:
  llm:
    extraction-provider: ${EXTRACTION_PROVIDER:gpt5mini}
    extraction-model: ${EXTRACTION_MODEL:gpt-5-mini}
    temperature: 0.0
  embedding: { model: ${EMBEDDING_MODEL:bge-m3}, dims: ${EMBEDDING_DIMS:1024} }
  translation: { provider: ${TRANSLATION_PROVIDER:bhashini} }
```

Enable scanning with `@ConfigurationPropertiesScan` on the main class. Keep the pgvector column
dimension equal to `naukri.embedding.dims` (1024 for bge-m3).

## Security & the REST contract

Phone + OTP → JWT, stateless sessions. Use the lambda DSL `SecurityFilterChain`; never disable CSRF
blindly without understanding the stateless-JWT context; store OTP in Redis with TTL; rate-limit OTP
(5/hr) and APIs (100/min) via Redis.

**For anything beyond the basics — the full phone+OTP→JWT flow, RS256 issuance/refresh/revocation,
the complete stateless `SecurityFilterChain`, role-based access (`CANDIDATE`/`EMPLOYER`), Redis rate
limiting, Twilio webhook signature verification, CORS, PII/DPDP handling, and the complete REST
contract (every endpoint, the `ProblemDetail` error shape, status-code conventions, pagination,
versioning, idempotency, and key request/response records) — read
[`references/security-and-api.md`](references/security-and-api.md).** Load it whenever a task touches
auth, secrets, PII, or designing/changing an endpoint.

## Observability (§17–18)

Expose Actuator + Prometheus (`/actuator/prometheus`); use Micrometer `@Observed`/timers for search
latency, parse time, delivery rate, and queue depth; wire the OpenTelemetry starter for traces.
Surface custom metrics the Grafana panels expect (`search_duration_seconds`,
`resume_parse_duration_seconds`, `notifications_sent_total`, stream pending count).

**Actuator runs on the internal management port** (`management.server.port: ${MANAGEMENT_PORT:8081}`,
`0` in tests, read with `@LocalManagementPort`). Probes and Prometheus target 8081, and the public
port 8080 maps no actuator endpoints. Never make an actuator endpoint public by path alone: Boot
applies the same filter chain on both ports, so a path-only `permitAll` would also open it on 8080.
Use `ManagementPortScrapeMatcher` (local-port based). Never publish 8081 or add it to the k8s
Service (see `k8s/network-policy.yaml`).

## Testing (Testcontainers + RestTestClient)

Containers are declared **once** as `@Bean`s in `TestcontainersConfiguration` (shared by every test
and by `TestNaukriNearbyApplication` for local dev) and imported. Don't declare per-class
`@Container` fields.

```java
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean @ServiceConnection
    ElasticsearchContainer elasticsearchContainer() {      // org.testcontainers.elasticsearch
        return new ElasticsearchContainer(DockerImageName.parse(
                "docker.elastic.co/elasticsearch/elasticsearch:9.4.5"))   // = Boot-managed ES client
                .withEnv("xpack.security.enabled", "false");
    }

    @Bean @ServiceConnection
    PostgreSQLContainer postgresContainer() {              // TC2: org.testcontainers.postgresql, NOT generic
        String image = new ImageFromDockerfile("naukrinearby/postgres-test:16", false)
                .withFileFromPath("Dockerfile", Path.of("docker/postgres/Dockerfile")).get(); // PostGIS+pgvector
        return new PostgreSQLContainer(DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"));
    }

    @Bean @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    }
    // Object storage (RustFS 1.0.1, S3-compatible) has no @ServiceConnection → DynamicPropertyRegistrar bean.
}

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")                                   // all providers stubbed, no external APIs
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JobSearchIntegrationTests {
    @LocalServerPort int port;

    @Test
    void searchReturnsNearbyJobs() {
        RestTestClient client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
        client.get().uri("/api/jobs/search?lat=22.72&lng=75.85&radius=5")
              .exchange().expectStatus().isOk();
    }
}
```

`@ServiceConnection` wires the container connection details, so no manual `@DynamicPropertySource`
is needed. Use `RestTestClient` (`org.springframework.test.web.servlet.client`) for HTTP-layer tests
rather than the older `WebTestClient`/`MockMvc` ceremony. Mock beans with `@MockitoBean`; `@MockBean`
is gone. Run `./gradlew build` (it needs a Docker daemon) before calling a change done.

## Bharat differentiators — implementation patterns

The differentiator milestone (`docs/plans/differentiators-implementation-plan.md`, ADRs 0007–0009)
adds these backend pieces. Follow the names and rules exactly; they are what the frontend, tests,
and runbook expect.

| Feature | Classes (package `com.naukrinearby…`) | Config / endpoints |
|---|---|---|
| Fraud scoring | `service.trust.JobRiskScorer` → `RiskAssessment(int score, List<String> reasons)` (record). Pure CPU, no network. Phrase lists in `resources/trust/payment-phrases.txt` (English, Hinglish, Devanagari; one per line, `#` comments) | `naukri.trust.review-threshold` (default 50) |
| Moderation | `Job.moderationStatus` (`ModerationStatus` enum: `APPROVED`, `PENDING_REVIEW`, `REJECTED`), `riskScore`, `riskReasons` (`text[]`); `service.ModerationService` | `GET /api/admin/moderation/jobs`, `POST /api/admin/moderation/jobs/{id}/decision` (ROLE_ADMIN) |
| Reports | `JobReport` entity, `UNIQUE(job_id, reporter_id)`; `ModerationService.report(...)` | `POST /api/jobs/{id}/report`; `naukri.trust.report-threshold` (default 3) |
| Trust levels | `service.trust.GstVerifier` port: `ChecksumGstVerifier` (`checksum`, default, GSTIN format + mod-36 check digit) / `NoopGstVerifier` (`none`); `TrustLevel` enum `UNVERIFIED`, `PHONE_VERIFIED`, `BUSINESS_VERIFIED` | `naukri.trust.gst-verifier`; `GET/PUT /api/employer/profile` |
| Admin bootstrap | `AuthService` gives `ROLE_ADMIN` to phones listed in `naukri.admin.phones` at OTP verify | `NAUKRI_ADMIN_PHONES` |
| Query normalization | `service.search.QueryNormalizer` loads `resources/lexicon/hinglish.tsv` (`variant<TAB>canonical`), used by `SearchService` (ES + PostGIS fallback) and `SkillGapService` | — |
| Skill gap | `service.SkillGapService` → `SkillGapResponse(matched, missing, suggestions)`; portals from `resources/trust/learning-portals.tsv` (official portals only, never invented course URLs) | `GET /api/candidate/jobs/{id}/skill-gap` |
| DPDP consent + export | `Consent` entity (append-only ledger, latest row per purpose wins), `ConsentPurpose` enum `RESUME_PROCESSING`, `WHATSAPP_ALERTS`; `service.ConsentService`; export in `AccountService` | `GET/PUT /api/candidate/consents`, `GET /api/candidate/account/export` |
| Language | `PUT /api/auth/me/language` updates `users.preferred_language` (`en`, `hi`, …) | — |
| IVR apply | `service.IvrService` (Redis state `ivr:call:{CallSid}`, TTL 15 min), `controller.IvrController`; TwiML built by `util.TwiML` (shared with the WhatsApp reply) | `POST /api/webhooks/twilio/voice`, `POST /api/webhooks/twilio/voice/gather/{menu|job|confirm}` (step is a path segment so the signed URL is the plain request URL); TwiML responses use `TwiML.CONTENT_TYPE` (explicit UTF-8, or Hindi is garbled) |
| Parse bulkhead | `@EnableResilientMethods` on a config class; `@ConcurrencyLimit` on the resume-parse entry point | `naukri.resume.parse-concurrency` (default 4) |
| LLM tracing | `generation.LlmTracer` port: `LogLlmTracer` (`log`), `LangfuseLlmTracer` (`langfuse`, ingestion API, async, failures swallowed), `NoopLlmTracer` (`none`, default) | `naukri.llm-trace.*` (`LLM_TRACER`, `LANGFUSE_HOST`, `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY`) |

Rules:

- **Moderation never bypasses the outbox.** A moderation decision or report escalation changes the
  job row, bumps `version`, and writes an outbox event in the same transaction. `OutboxSyncWorker`
  **deletes** the ES document when the payload's `moderationStatus` is not `APPROVED`. Every public
  read path (ES search, PostGIS fallback, `GET /api/jobs/{slug}`, recommendations) shows only
  `APPROVED` + `ACTIVE` jobs; the owning employer and admins may read their own pending jobs.
- **Scoring is synchronous and offline.** Never call an external API from `JobRiskScorer`; a future
  ML scorer goes behind the same type and runs async.
- **Candidates never see the numeric risk score.** Public DTOs expose `trustLevel` and
  `employerSince` only; risk reasons go to the employer (their own job) and admins.
- **Webhooks:** every Twilio webhook (status, inbound message, voice) verifies
  `X-Twilio-Signature` before doing anything, and returns TwiML escaped through `util.TwiML`
  (`HtmlUtils.htmlEscape(text, "UTF-8")`). Never concatenate raw user text into XML.
- **Regex on user input** must be linear: bounded quantifiers (`\D{0,20}`), no nested `.*`, and a
  length cap on the input before matching (CodeQL `java/polynomial-redos`).
- **No PII in traces or logs:** `LlmTracer` sends provider, model, prompt version, latency, JSON
  validity, and character counts only; phones go through `PiiRedactor`.
- **Migrations are expand-only** for this milestone (`V5__trust_and_moderation.sql`,
  `V6__consents.sql`): nullable or defaulted columns, new tables, no renames or drops.

## Anti-patterns to fix on sight

| Anti-pattern | Fix |
|---|---|
| Field injection (`@Autowired` on fields) | constructor injection (`@RequiredArgsConstructor`) |
| Business logic / external calls in controllers | move to a `@Service`; controller stays thin |
| `@Transactional` on controller or private method | put it on a public service method |
| Dual write to Postgres + ES | transactional outbox + idempotent sync worker |
| `RestTemplate`/`WebClient` boilerplate per provider | `@HttpExchange` HTTP Service Client |
| Hand-rolled retry loops | R4j `@Retry` + `@CircuitBreaker` (project convention), configured in YAML |
| Deprecated Boot/Framework API, `@MockBean`, Jackson 2 `com.fasterxml.jackson.databind` | the non-deprecated replacement, `@MockitoBean`, `tools.jackson.databind` |
| ES 8.x image / version drift from the Boot-managed ES client | `elasticsearch:9.4.5` in Compose **and** Testcontainers |
| Pinning a Boot-managed dependency version | drop the version and let the Boot BOM manage it |
| Scattered `@Value` | `@ConfigurationProperties` record |
| `ResponseEntity<ErrorResponse>` ad-hoc errors | `ProblemDetail` via `@RestControllerAdvice` |
| Mapping lazy entities in a loop | `JOIN FETCH` / `@EntityGraph` / DTO projection |
| Returning Spring Data `Page<T>` from a controller | `PageResponse.from(page, Dto::from)` (stable JSON, no PageImpl warning) |
| Hardcoded API keys | env-bound `@ConfigurationProperties` |
| Writing ES directly on a moderation decision | update the row + outbox event; the sync worker deletes/upserts |
| String-concatenated TwiML/XML with user text | `util.TwiML` (escaped) |
| Unbounded regex (`\D*`, `.*x.*`) on request input | bounded quantifiers + input length cap |
| Sending resume text or phone numbers to Langfuse/logs | metadata-only `LlmTracer`, `PiiRedactor` |

## Quick reference

- Versions: Spring Boot **4.1.1**, Spring Framework 7.0.9, Spring Security 7.1, Hibernate 7.4,
  ES client/server **9.4.5**, Java 21, Gradle 9.5.1, `resilience4j-spring-boot4` 2.4.0.
- Upgrade hygiene: no deprecated APIs (`-Xlint:deprecation`), no pinned Boot-managed versions.
- Adapters → `@HttpExchange`/`RestClient`; resilience → Resilience4j `@Retry` + `@CircuitBreaker`.
- Consistency → transactional outbox (never dual writes); idempotent ES upsert on `job_id`.
- Errors → `ProblemDetail`; config → `@ConfigurationProperties`; null-safety → JSpecify.
- Tests → Testcontainers + `@ServiceConnection` + `RestTestClient`.
- Security & full REST contract (auth flow, JWT, rate limits, webhook verification, endpoints,
  error shapes) → `references/security-and-api.md`.
- Virtual threads on (`spring.threads.virtual.enabled=true`); language-level details in
  naukrinearby-java21.
- Differentiators (fraud/moderation, trust, IVR, skill gap, consent, query normalization,
  `@ConcurrencyLimit`, `LlmTracer`) → "Bharat differentiators" section above and ADRs 0007–0009.
