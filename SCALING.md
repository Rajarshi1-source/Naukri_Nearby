# Scaling NaukriNearby to Millions of Users

This document records the architectural and system-design decisions that let NaukriNearby grow from
a single-node demo to a national, multi-million-user job platform for Tier-2/3 India. It separates
**what the code already does** (so scaling is configuration and capacity, not a rewrite) from
**what changes at each growth stage**.

Related: [README](README.md) · [ADRs](docs/adr/) · [operations runbook](docs/runbooks/operations.md) ·
[differentiators plan](docs/plans/differentiators-implementation-plan.md).

---

## 1. Load model

Sizing assumptions for the target stage (10M registered candidates, 500k employers):

| Signal | Estimate | Why it matters |
| --- | --- | --- |
| Daily active users | 1–1.5M (10–15% DAU/MAU, mobile-heavy) | API and CDN capacity |
| Peak search QPS | ~3,000 (evening peak ≈ 4× average) | ES cluster and Redis cache size |
| Job posts / day | ~50k creates + 150k edits | Outbox throughput, ES indexing rate |
| Resume parses / day | ~100k (uploads + WhatsApp/IVR transcripts) | LLM cost and parse-worker pool |
| WhatsApp alerts / day | 2–5M (capped per user per day) | Notification workers, Twilio tier limits |
| Read : write | ~50 : 1 | Read path is where scale is won |

The workload is **read-heavy, geo-local, and bursty**. Almost every decision below follows from
that: cache and index the reads, keep the write path small and transactional, and push slow work
to queues.

---

## 2. Principles already in the code

These are the decisions that make horizontal scaling possible. None of them change at scale.

| Decision | Where | Why it scales |
| --- | --- | --- |
| **Stateless API** — JWT (RS256) auth, refresh tokens in Redis, no HTTP session | `JwtService`, `SecurityConfig` | Any pod serves any request; HPA can add pods freely. Public key verification needs no shared state. |
| **Postgres is the source of truth (CP); Elasticsearch is a derived index (AP)** | ADR 0001 | Writes stay ACID on one store; the read store can lag and be rebuilt at any time. |
| **Transactional outbox, never dual-write** | `JobService`, `OutboxSyncWorker` | Index updates survive ES outages and can be drained at any rate. `FOR UPDATE SKIP LOCKED` lets many sync workers share the outbox without coordination. |
| **Idempotent consumers** — ES upsert keyed on `job_id` with a version guard; alert dedup on `(user, job)` via Redis `SETNX` + `UNIQUE` row | `OutboxSyncWorker`, `NotificationService` | At-least-once delivery + idempotent effect = effectively-once, so retries and redeliveries are safe at any scale. |
| **Graceful degradation** — ES down → PostGIS `ST_DWithin`; translation down → English; provider down → circuit breaker | `SearchService`, `TranslationService`, Resilience4j | A partial outage degrades quality, not availability. |
| **Split API and worker tiers** — `api` profile turns workers off | `@Profile("!api")`, `k8s/` | Each tier scales on its own signal (RPS vs queue depth). |
| **Bulkheads** — resume parsing bounded by `@ConcurrencyLimit`; separate worker pools | `ResumeParserService` | Parse spikes cannot starve alerts or search. |
| **Provider adapters selected by config** | ADR 0002 | Swap a vendor (or self-host a model) under load without touching call sites. |
| **Alias-based ES index** (`jobs` → `jobs_vN`) | ADR 0003 | Re-shard or re-map with a reindex and an atomic alias flip, no downtime. |
| **Expand/contract migrations** | ADR 0004 | Rolling deploys of many pods never break on schema changes. |
| **Moderation gates the index, not the write** | ADR 0007 | Fraud review adds no latency to posting; only approved jobs reach the read path. |
| **Internal management port** (8081) | `ManagementPortScrapeMatcher` | Metrics scraping scales with pods without opening actuator to the internet. |

---

## 3. Growth stages

### Stage 0 — demo / pilot (≤ 50k users)
Single Postgres, single-node ES, single Redis, 2 API pods, 1 worker pod, RustFS single node. This
is what `compose.yaml` and `k8s/` describe today.

### Stage 1 — one state / a few cities (≤ 1M users)
- **API**: HPA 2→10 pods on CPU and RPS. Virtual threads already on; size the Hikari pool per pod
  (≈ 10–20) and put **PgBouncer** (transaction mode) in front of Postgres so pod count does not
  multiply connections.
- **Postgres**: managed primary + 1 read replica (Multi-AZ). Route read-only, latency-tolerant
  queries (recommendations, employer dashboards, PostGIS fallback) to the replica via a
  routing `DataSource`; keep read-your-writes paths (poster's own jobs) on the primary.
- **Elasticsearch**: 3 nodes, 1 replica, `jobs` with 3 primary shards.
- **Redis**: primary + replica with Sentinel; separate logical roles by key prefix.
- **CDN** for the Next.js static assets and the PWA shell; SSR job pages cached at the edge for
  30–60 s (`s-maxage`), since job pages are public and SEO-critical.

### Stage 2 — national (≤ 10M users)
- **Split Redis by role** into separate clusters: (a) cache, (b) Streams/queues, (c) rate limits +
  refresh tokens + idempotency keys. Eviction policy differs per role (`allkeys-lru` for cache,
  `noeviction` for queues and tokens). Losing the cache must never drop a queued alert.
- **Worker tiers by job type**: `sync` (outbox → ES), `notify` (alerts), `parse` (resume/LLM).
  Each scales on its own metric: outbox lag, `notification_queue_depth`, parse queue depth. Redis
  Streams consumer names already come from the pod name (`consumer-name: ${HOSTNAME:worker-1}`);
  add an `XAUTOCLAIM` sweeper so a dead pod's pending entries are picked up by the survivors.
- **Outbox → CDC**: replace polling with Debezium tailing the WAL into Kafka (or Redis Streams)
  when outbox volume exceeds ~1k events/s. The outbox table stays as the contract, so the change
  is invisible to the write path.
- **Elasticsearch**: hot/warm nodes; index per region (`jobs_north`, `jobs_south`, …) behind the
  `jobs` alias; **routing by geohash prefix** so a radius query hits one or two shards instead of
  all. Closed/expired jobs move to a warm index via ILM.
- **Postgres partitioning**: `applications`, `notification_logs`, `outbox_events` partitioned by
  month (`pg_partman`); processed outbox partitions dropped instead of `DELETE`d. `jobs` partitioned
  by state if a single table passes ~100M rows.
- **pgvector**: HNSW indexes already used; at this scale move candidate-to-job vector matching to
  ES `dense_vector` kNN (or a dedicated vector store) so Postgres stays the system of record, not
  the ANN engine.

### Stage 3 — tens of millions
- **Regional cells**: deploy independent cells per region (API + workers + ES + Redis), sharing a
  globally replicated Postgres (or Citus sharded by `state`). Users are pinned to the cell of their
  home location; cross-cell search is rare because the product is hyperlocal by design.
- **Data residency**: all cells stay in Indian regions (DPDP Act). Backups, logs, and LLM calls
  stay in-region; the `LlmTracer` never ships resume text.
- **Self-hosted models** for embeddings (bge-m3 on GPU nodes) and a distilled extraction model for
  the common resume shapes, keeping the hosted LLM for low-confidence parses only.

---

## 4. Read path at scale

```
Browser / PWA ─► CDN (static, SSR job pages s-maxage) ─► Ingress ─► API pods
                                                               │
                         Redis cache (search key = text+category+cell+radius+size)
                                                               │ miss
                                                   Elasticsearch (geo_distance, alias jobs)
                                                               │ failure
                                                   PostGIS ST_DWithin on read replica
```

- **Cache key by location cell.** Today the key rounds lat/lng to 3 decimals (~110 m). At Stage 2
  switch to a geohash-6 cell (~1 km) so nearby users share entries; the radius already dominates
  the result set. TTL is short (5 min); new jobs appear within the outbox lag plus TTL, which is
  acceptable for search (AP).
- **Request coalescing**: one in-flight ES query per cache key (Redis lock or in-process
  single-flight) to stop thundering herds on cold keys at peak.
- **Pagination caps**: `max-results` and radius caps (`naukri.geo.max-radius-km`) bound every
  query; deep pagination uses `search_after`.
- **PWA offline cache** absorbs repeat views on flaky 2G/3G networks and removes load that would
  otherwise hit the API.

## 5. Write path at scale

- Writes are small: one row + one outbox row in one transaction. No external call inside a
  transaction (LLM, ES, Twilio are all after-commit or async).
- **Optimistic concurrency** (`version`) on jobs; the version travels in the outbox so ES never
  applies a stale update.
- **Fraud scoring is synchronous but CPU-only** (rules, no network), so it adds < 1 ms per post. A
  future ML scorer runs async and moves a job to `PENDING_REVIEW` through the same outbox path.
- **Resume parsing is async**: upload returns `202` with a `resumeId`; the client polls. Parse
  results are cached by file hash so re-uploads cost nothing.

## 6. Messaging at scale (WhatsApp, IVR)

- Alerts fan out through Redis Streams with consumer groups; DLQ after N retries; per-user daily
  cap (`naukri.notification.daily-limit`) bounds cost and spam.
- **Twilio throughput**: shard senders across multiple WhatsApp sender numbers and respect
  messaging-tier limits; a token bucket in Redis per sender enforces it.
- **IVR** is stateless per request (state in Redis by `CallSid`), so voice webhooks scale with the
  API tier. Long menus are avoided because every turn is an HTTP round trip.
- Webhooks are verified (`X-Twilio-Signature`) and deduplicated (`MessageSid`, `CallSid`), so
  Twilio retries at scale are harmless.

## 7. AI cost and capacity

| Component | Scaling decision |
| --- | --- |
| Resume extraction | Mid-tier model; cache by file hash; `@ConcurrencyLimit` bulkhead; daily token budget alarm; frontier model only for low-confidence parses |
| Embeddings | Self-hosted bge-m3 (`EMBEDDING_PROVIDER=remote`), batch requests, cache by normalized skill set |
| Translation | Bhashini with template caching per (alert template, language); English fallback |
| Fraud scoring | Rules today (zero marginal cost); ML scorer later, async |
| Evaluation | Eval gate in CI and canary on extraction quality, so a cheaper model can be rolled out safely |

## 8. Reliability targets

| SLO | Target | Mechanism |
| --- | --- | --- |
| Search availability | 99.9% | ES cluster + PostGIS fallback + cache |
| Search p95 latency | < 300 ms (cache hit < 50 ms) | geohash cache, shard routing |
| Post-to-searchable lag | p95 < 10 s | outbox polling interval, sync worker HPA on lag |
| Alert duplicate rate | 0 | dedup key + `UNIQUE` backstop |
| RPO / RTO (Postgres) | 5 min / 30 min | PITR backups, Multi-AZ failover; ES rebuilt from Postgres via reindex |

Elasticsearch is never backed up as a source of truth: it is rebuilt from Postgres (`outbox`
replay or a full reindex into a new `jobs_vN` and an alias flip).

## 9. Security and abuse at scale

- Rate limits in Redis (OTP 5/hour per phone; API 100/min per user or IP) move to the edge
  (WAF / API gateway) at Stage 2, with Redis limits kept as the second layer.
- OTP abuse (SMS pumping) is bounded per phone, per IP, and per country prefix.
- Fraud: rule scores + report threshold + admin queue today; at scale add employer-level risk
  (velocity of posts, repeated payment keywords) and device fingerprints.
- Secrets come from the environment (`@ConfigurationProperties`), backed by a secrets manager;
  JWT signing keys rotate with a `kid` and a two-key overlap window.

## 10. Observability at scale

- Metrics: Micrometer → Prometheus (scraped on 8081), long-term in Thanos/Mimir; Grafana KPIs
  already provisioned (search latency, parse latency, queue depth, DLQ depth, breaker state).
- Tracing: OpenTelemetry (Micrometer Tracing bridge already present) with tail sampling.
- LLM observability: Langfuse via `LlmTracer` (metadata only).
- Alerts on: outbox lag, DLQ growth, breaker open, ES fallback rate, moderation queue age.

## 11. Deployment at scale

- Rolling updates (`maxUnavailable=0`) for the API; blue/green for ES mapping changes (alias
  flip); canary on **extraction quality** for prompt/model changes.
- Expand/contract migrations, so mixed-version pods are always safe.
- PWA service worker versioned per build; old caches are deleted on `activate`, so a deploy never
  serves a stale app shell indefinitely.

## 12. What would change first under real load

1. PgBouncer + read replica routing (cheapest, biggest win).
2. `XAUTOCLAIM` sweeper for the Redis Stream consumers (consumer names are already per pod).
3. Redis split by role.
4. Geohash cache keys + request coalescing.
5. Outbox partitioning, then CDC.
6. Regional ES indices with geohash routing.
