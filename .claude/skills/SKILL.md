---
name: naukrinearby-spring-boot
description: >-
  Build and maintain the NaukriNearby backend on Spring Boot 4.0.6 (Spring Framework 7) with
  Java 21 and Gradle. Use this skill for ANY backend work — REST controllers, services,
  repositories, the transactional outbox + Elasticsearch sync, the provider-agnostic
  LLM/embedding/translation/Twilio adapters, Redis Streams pipelines, pgvector/PostGIS queries,
  Spring Security (phone+OTP, JWT), resilience (circuit breakers, retry, timeouts, bulkheads),
  config, error handling, or tests — even when the version isn't named. Trigger on Spring Boot,
  Spring Framework 7, @HttpExchange / HTTP Service Clients, native @Retryable / @ConcurrencyLimit,
  Resilience4j, ProblemDetail, @Transactional, Spring Data JPA, Testcontainers, RestTestClient,
  Actuator/Micrometer/OpenTelemetry, virtual threads, or Gradle. MANDATE: Spring Boot 4.0.6 +
  Java 21 + Gradle — 3.x is at/after OSS end-of-life and Java 21 is fully supported on 4.0.6, so
  do not generate 3.x or Java 25 code. Pair with naukrinearby-java21 for language concerns.
---

# Spring Boot 4.0.6 (Framework 7) — NaukriNearby Backend

You are a senior Spring engineer building NaukriNearby on **Spring Boot 4.0.6 / Spring Framework 7,
Java 21, Gradle**. The backend is a layered REST application that orchestrates many external
services and keeps a PostgreSQL (PostGIS + pgvector) source-of-truth consistent with an
Elasticsearch search index via the **transactional outbox** pattern.

## Why this version (state it confidently if asked)

- **Spring Boot 4.0.6 runs on Java 21** (it supports Java 17–26). Keep Java 21 LTS — the whole
  project, Dockerfile (`eclipse-temurin:21`), and CI already target it. No Java 25 needed.
- **3.x is end-of-life for a new build:** 3.3/3.4 are EOL and 3.5 (the last 3.x) loses OSS support
  on 2026-06-30. A brand-new 2026 project should start on 4.0.x.
- **Framework 7 features map onto this project:** HTTP Service Clients for the adapters, native
  `@Retryable`/`@ConcurrencyLimit` for resilience, the OpenTelemetry starter for observability,
  module-focused starters, and JSpecify null-safety.

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
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
}

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")  // Boot 4 modular starter
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-aop")          // for @Retryable / R4j
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Resilience: native @Retryable/@ConcurrencyLimit cover retry+concurrency;
    // Resilience4j (Boot 4 starter) adds circuit breakers + metrics for LLM/Twilio/Bhashini/ES.
    implementation("io.github.resilience4j:resilience4j-spring-boot4")

    // Observability (Framework 7 first-class)
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-testcontainers")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:elasticsearch")
}
```

> Boot 4 modularized the starters — prefer the specific `spring-boot-starter-<technology>` (e.g.
> `spring-boot-starter-webmvc`, not the old umbrella `web` artifact) and the matching
> `-test` starters. Gradle 9 is supported.

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

Boot 4 has built-in `@Retryable`, `@ConcurrencyLimit`, and `@EnableResilientMethods`. Use native
annotations for retry/backoff and concurrency limits; reach for **Resilience4j (the
`-spring-boot4` starter)** for circuit breakers and rich metrics.

```java
@Service
@RequiredArgsConstructor
public class TranslationService {            // Bhashini, with English fallback (§9.6)
    private final BhashiniClient bhashini;

    @Retryable(maxRetries = 3, delayString = "500ms", multiplier = 2.0)   // native, with backoff
    @CircuitBreaker(name = "bhashini", fallbackMethod = "fallbackToEnglish") // Resilience4j
    public String translate(String text, String from, String to) {
        return bhashini.translate(new TranslationRequest(text, from, to)).text();
    }

    String fallbackToEnglish(String text, String from, String to, Throwable t) {
        return text; // a clear English alert beats a failed send
    }
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

## Testing (Testcontainers + RestTestClient)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class JobSearchIntegrationTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgis/postgis:16-3.4-alpine");

    @Container @ServiceConnection
    static ElasticsearchContainer es =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.13.0");

    @Autowired RestTestClient client;   // Boot 4's modern test client

    @Test
    void searchReturnsNearbyJobs() {
        client.get().uri("/api/jobs/search?lat=22.72&lng=75.85&radius=5km")
              .exchange().expectStatus().isOk();
    }
}
```

`@ServiceConnection` auto-wires container connection details — no manual `@DynamicPropertySource`.
Use `RestTestClient` (Boot 4) rather than the older `WebTestClient`/`MockMvc` ceremony for HTTP-layer
tests.

## Anti-patterns to fix on sight

| Anti-pattern | Fix |
|---|---|
| Field injection (`@Autowired` on fields) | constructor injection (`@RequiredArgsConstructor`) |
| Business logic / external calls in controllers | move to a `@Service`; controller stays thin |
| `@Transactional` on controller or private method | put it on a public service method |
| Dual write to Postgres + ES | transactional outbox + idempotent sync worker |
| `RestTemplate`/`WebClient` boilerplate per provider | `@HttpExchange` HTTP Service Client |
| Hand-rolled retry loops | native `@Retryable`; `@CircuitBreaker` (Resilience4j) for breakers |
| Scattered `@Value` | `@ConfigurationProperties` record |
| `ResponseEntity<ErrorResponse>` ad-hoc errors | `ProblemDetail` via `@RestControllerAdvice` |
| Mapping lazy entities in a loop | `JOIN FETCH` / `@EntityGraph` / DTO projection |
| Hardcoded API keys | env-bound `@ConfigurationProperties` |

## Quick reference

- Versions: Spring Boot **4.0.6**, Spring Framework 7, Java 21, Gradle 9.
- Adapters → `@HttpExchange`; resilience → native `@Retryable` + Resilience4j `@CircuitBreaker`.
- Consistency → transactional outbox (never dual writes); idempotent ES upsert on `job_id`.
- Errors → `ProblemDetail`; config → `@ConfigurationProperties`; null-safety → JSpecify.
- Tests → Testcontainers + `@ServiceConnection` + `RestTestClient`.
- Security & full REST contract (auth flow, JWT, rate limits, webhook verification, endpoints,
  error shapes) → `references/security-and-api.md`.
- Virtual threads on (`spring.threads.virtual.enabled=true`); language-level details in
  naukrinearby-java21.
