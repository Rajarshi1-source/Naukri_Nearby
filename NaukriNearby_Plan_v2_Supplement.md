# NaukriNearby — Implementation Plan v2 (Gap-Fill Supplement)
## The Missing Sections + Model Update + Bharat Differentiators

> **How to use this:** Your existing `NaukriNearby_Implementation_Plan.md` is strong on Tech Stack, MVP, HLD, LLD (resume parser / geo search / WhatsApp pipeline), Database Design + Choice, Caching, Design Patterns, Docker/K8s, CI/CD, Monitoring, README, and Interview Prep. This supplement adds the **9 things missing or stale**, each labeled with where it slots in. Bolt these in and the plan is complete *and* Bharat-differentiated.

---

## Gap-Analysis Verdict (verified by keyword scan)

| Requirement | In the file? | Gap |
|---|---|---|
| **Availability & Consistency Patterns** | ❌ Absent | 0 mentions of CAP/availability. And the **PostgreSQL↔Elasticsearch dual-store** sync problem is never addressed — the single biggest correctness gap. |
| **Mitigation strategies** | ❌ Absent | 0 mentions as a section. |
| **Resilience patterns** | ⚠️ Scattered | Only circuit breaker (§8.6). No DLQ, no idempotency, no consolidated section. |
| **DevOps/MLOps wrapper** | ❌ Absent | 0 mentions. There are **three** AI components (resume extraction, vector match, translation) with **no quality measurement** — the biggest gap for an AI product. |
| **Deployment strategies** | ❌ Absent | Has a checklist; no rolling/blue-green/canary. |
| **Detailed System Architecture** | ⚠️ Partial | HLD shows logic flow; no infra/deployment topology with the dual-store. |
| **Model choice (GPT-5?)** | ❌ Stale | Uses "GPT-4" 30× including GPT-4 Vision. Dated for 2026. |
| **Idempotency** | ❌ Absent | 0 mentions — and WhatsApp jobs are at-least-once, so you risk **double-texting users job alerts**. Real bug. |
| **Detailed System Architecture / topology** | ❌ Absent | See above. |

**Everything else you asked for is present and good — don't rewrite it.**

---

## Table of Contents (this supplement)

- [§0. Model Choice Update — GPT-5 / Current Models + Bhashini for Indian languages](#s0)
- [§A. Detailed System Architecture (infra topology with dual-store) — insert after §4](#sa)
- [§B. Availability & Consistency Patterns (the star section — PG↔ES sync) — insert as §6.5](#sb)
- [§C. Resilience Patterns (consolidated) — insert as §8.5](#sc)
- [§D. Mitigation Strategies — insert as §8.6](#sd)
- [§E. DevOps / MLOps Wrapper (three AI components) — insert as §11.5](#se)
- [§F. Deployment Strategies (rolling / blue-green / canary) — insert into §9](#sf)
- [§G. Bharat Differentiator Features — NEW section](#sg)
- [§H. Extra Interview Q&A](#sh)

---

<a id="s0"></a>
## §0. Model Choice Update — GPT-5 / Current Models + Bhashini

**Replaces every `GPT-4` / `GPT-4 Vision` reference in the original.**

### The verdict on your GPT-5 question

For 2026, yes — move off GPT-4. But this project has **three distinct AI jobs**, and the right model differs for each. Hardcoding one vendor (the original does, 30×) is the junior move. Put each behind an adapter and pick per task:

| AI job | Recommended | Why |
|---|---|---|
| **Resume → structured JSON extraction** | A current **mid-tier** model (GPT-5-mini-class / Claude Haiku-class) behind an adapter | Structured extraction is not the hardest task; a mid-tier model with a tight schema + few-shot is accurate and ~10× cheaper than frontier. Resume parsing is high-volume → cost matters. |
| **Resume image → text (OCR/Vision)** | A current vision model OR a dedicated OCR (Google Vision / Tesseract+postproc) | For Indian resumes (mixed Hindi/English, photos), a vision model handles layout; dedicated OCR is cheaper if volume is high. |
| **Skill/job semantic match** | An **embedding** model (open `bge-m3` / `gte-multilingual`, or a hosted embedding model) | `bge-m3` is multilingual — critical for Hindi/regional skill text. Self-hostable → no per-call cost. |
| **Regional-language translation** | **Bhashini** (India's govt translation initiative) or Google Translate — *not* a general LLM | Bhashini is purpose-built for 22 Indian languages and is the credible Bharat choice. General LLM translation is weaker on low-resource Indian languages and costs more. |

> **Bharat interview gold:** *"For regional-language alerts I'd use Bhashini — the Government of India's translation stack built for 22 Indian languages — rather than a general LLM. It's more accurate on low-resource languages like Bhojpuri or Maithili, it's the credible choice for a Bharat product, and it signals I researched the India-specific tooling, not just reached for OpenAI."*

### The adapter (replaces direct OpenAI calls)

```java
public interface LlmExtractionProvider {
    <T> T extractStructured(String text, Class<T> schema, String promptVersion);
    String providerId();
}
// Implementations: Gpt5MiniProvider, ClaudeHaikuProvider, LocalModelProvider
// Selected via application.yml: naukri.llm.extraction-provider = gpt5mini | claude | local
```

```yaml
naukri:
  llm:
    extraction-provider: ${EXTRACTION_PROVIDER:gpt5mini}
    extraction-model: ${EXTRACTION_MODEL:gpt-5-mini}
    vision-model: ${VISION_MODEL:gpt-5-mini}
    temperature: 0.0                          # deterministic extraction
  embedding:
    model: ${EMBEDDING_MODEL:bge-m3}          # multilingual, self-hostable
    dims: 1024
  translation:
    provider: ${TRANSLATION_PROVIDER:bhashini} # bhashini | google
```

> **Cost interview gold:** *"Resume parsing is my highest-volume LLM call, so I deliberately used a mid-tier model, not a frontier one — structured extraction with a strict JSON schema doesn't need GPT-5-full. I reserve any frontier model for genuinely ambiguous resumes. And I use a self-hostable multilingual embedding model (bge-m3) so the vector-match step has zero per-call cost."*

---

<a id="sa"></a>
## §A. Detailed System Architecture (Infra Topology)

**Insert after §4 (HLD). Shows deployment topology + the dual-store, vs the HLD's logic flow.**

```
                          ┌────────────────────────────────────────┐
   User (browser/PWA) ───►│        INGRESS / LB (nginx)             │
   Tier-2/3, flaky net    │        TLS · gzip · CDN for static      │
                          └───────────────┬────────────────────────┘
              ┌───────────────────────────┼───────────────────────────┐
              ▼                           ▼                           ▼
     ┌─────────────────┐        ┌─────────────────┐        ┌──────────────────┐
     │ Next.js (SSR)   │        │ Spring Boot     │        │ Spring Boot      │
     │ frontend pods   │        │ API pods (2-5)  │        │ worker pods      │
     │ (PWA, offline)  │        │ • search        │        │ • resume parse   │
     └─────────────────┘        │ • job CRUD      │        │ • PG→ES sync     │
                                │ • match         │        │ • WhatsApp send  │
                                └────────┬────────┘        └────────┬─────────┘
                                         │                          │
        ┌────────────────────────────────┼──────────────────────────┼──────────────┐
        ▼                ▼                ▼                          ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐        ┌─────────────────┐ ┌──────────┐
│ PostgreSQL   │ │ Elasticsearch│ │ Redis        │        │ LLM / Embedding │ │ Bhashini │
│ +PostGIS     │ │ (search      │ │ • cache      │        │ (adapter) +     │ │ + Twilio │
│ +pgvector    │ │  INDEX, geo) │ │ • Streams    │        │ Langfuse trace  │ │ WhatsApp │
│ SOURCE OF    │◄┤ eventually   │ │ • idempotency│        └─────────────────┘ └──────────┘
│ TRUTH        │ │ consistent   │ │ • dedup      │
└──────┬───────┘ └──────▲───────┘ └──────────────┘
       │  outbox events  │
       └─────────────────┘   (PG→ES sync via outbox pattern — see §B)

SCALING UNITS:
  • frontend — static + SSR, CDN-fronted; scales freely
  • api      — stateless → HPA on CPU/RPS
  • workers  — split deployment: parse / sync / notify scale independently on queue depth
  • postgres — primary + read replica (source of truth, CP)
  • elasticsearch — 3-node cluster at scale (search index, AP)
  • redis    — single node MVP; Sentinel at scale

DATA ZONES (India-resident for DPDP Act compliance):
  • public:  ingress, frontend
  • app:     api, workers
  • data:    postgres, elasticsearch, redis (NetworkPolicy-restricted, India region)
```

**Key decision to articulate:** *PostgreSQL is the source of truth (CP); Elasticsearch is a derived search index (AP). They're kept eventually consistent via an outbox pattern (§B), not dual writes. Workers are split by job type so resume-parsing load can't starve WhatsApp delivery.*

---

<a id="sb"></a>
## §B. Availability & Consistency Patterns

**Insert as §6.5. This is the strongest section for NaukriNearby because the PG↔ES dual-store is a textbook consistency problem your plan currently hand-waves.**

### B.1 CAP positioning — two planes

| Path | Regime | Why |
|---|---|---|
| **Job search** (Elasticsearch geo) | **AP** | A searcher prefers fast, available results that may lag the DB by seconds over a blocked search. |
| **Job posting / application / profile** (Postgres) | **CP** | A posted job, a submitted application, or a parsed profile must commit atomically. No ghost records. |

> **Interview gold:** *"PostgreSQL is my source of truth and runs CP — posting a job is one ACID transaction. Elasticsearch is a derived search index and runs AP — searches are fast and available even if the index lags the DB by a few seconds. The whole design is recognizing that 'post a job' and 'search jobs' have opposite consistency needs."*

### B.2 The PG→ES sync problem (the gap your plan must close)

The original writes to Postgres *and* indexes to Elasticsearch — but never says how they stay consistent. **Dual writes are a trap:** if the ES write fails after the PG commit, the job exists but is unsearchable (or vice versa) — silent data divergence. The correct answer is the **Transactional Outbox pattern**:

```
1. In ONE Postgres transaction: write the job row AND an "outbox" event row.
   (Atomic — both commit or neither.)
2. A separate sync worker polls the outbox table (or tails the WAL via Debezium/CDC),
   reads unprocessed events, and indexes them into Elasticsearch.
3. On success, mark the outbox event processed. On failure, retry (it's still there).
```

This guarantees **eventual consistency** with no lost updates: the job is durably in Postgres, and the index *will* catch up because the event is durably queued. (Starter code provided separately.)

> **Interview gold:** *"I don't dual-write to Postgres and Elasticsearch — that risks divergence if one write fails. I use the transactional outbox pattern: the job and an outbox event commit in one Postgres transaction, then a sync worker drains the outbox into Elasticsearch with retries. At-least-once indexing plus idempotent upserts keyed on job_id gives effectively-once indexing."*

### B.3 Read-your-writes for the job poster

A recruiter who just posted a job must see it immediately — but ES may not have indexed it yet (lag). Pattern: after posting, the recruiter's "my jobs" view reads from **Postgres (source of truth)**, not Elasticsearch. Only the public search reads from ES. So the poster always sees their own write instantly; eventual consistency is invisible to them.

### B.4 Idempotent WhatsApp notifications (CRITICAL — currently missing)

Notification jobs are processed at-least-once (worker crash → redelivery). Without dedup you'd **text the same job alert to a user twice** — a terrible experience and a Twilio cost. Dedup on `(user_id, job_id)`:

```java
// Only the first send for this (user, job) proceeds.
String key = "notif:sent:" + userId + ":" + jobId;
Boolean isNew = redis.opsForValue().setIfAbsent(key, "1", Duration.ofDays(7));
if (Boolean.FALSE.equals(isNew)) return;   // already notified — skip
whatsAppService.send(userId, jobId);
```

Also persist a `notification_log` row with a `UNIQUE(user_id, job_id)` constraint as the DB-level backstop.

### B.5 At-least-once + idempotent consumer = effectively-once
Both the PG→ES sync and the WhatsApp pipeline use Redis Streams (at-least-once). Idempotent effects (ES upsert keyed on `job_id`; WhatsApp dedup keyed on `(user_id, job_id)`) make the end result effectively-once. Say exactly that phrase.

### B.6 Availability tactics
- **Postgres:** primary + read replica; automated failover (Patroni / managed RDS Multi-AZ).
- **Elasticsearch:** 3-node cluster with 1 replica shard at scale (single node acceptable for MVP — conscious trade-off). If ES is down, search degrades gracefully to a **PostGIS fallback query** (slower, but the site still works).
- **Graceful degradation:** ES down → fall back to PostGIS `ST_DWithin` geo query (your DB already has PostGIS!). Slower, but available. This is a *huge* resilience win that your dual-store accidentally gives you for free.

> **Interview gold:** *"Because PostGIS is already in my source-of-truth Postgres, if Elasticsearch goes down I fall back to a PostGIS ST_DWithin radius query. Search gets slower and loses fuzzy text matching, but the site stays up. My dual-store gives me a free availability backstop."*

### B.7 Concurrent updates
Job edits use optimistic concurrency (a `version` column); the outbox event carries the version so ES never indexes a stale update over a newer one (check version on upsert).

---

<a id="sc"></a>
## §C. Resilience Patterns (Consolidated)

**Insert as §8.5.**

| Pattern | Where | Implementation |
|---|---|---|
| **Circuit breaker** | LLM, Bhashini, Twilio, ES | Resilience4j; open at 50% failure / 20 calls; fallbacks per service |
| **Retry + backoff + jitter** | LLM 429s, Twilio, ES blips | Max 3; respect `Retry-After`; jitter |
| **Timeout** | Every external call | LLM 60s, Twilio 10s, ES 3s |
| **Bulkhead** | parse / sync / notify worker pools | Separate pools so resume-parse load can't starve WhatsApp delivery |
| **Rate limiter** | LLM + Twilio + Bhashini | Redis sliding window; respect Twilio's WhatsApp tier limits |
| **Dead-letter queue** | Failed parse / sync / notify jobs | After N retries → DLQ stream + alert; job flagged for manual review |
| **Graceful degradation** | ES outage → **PostGIS fallback** (§B.6); translation outage → send English | Site stays usable |
| **Idempotency** | WhatsApp sends + ES indexing | dedup `(user_id, job_id)`; ES upsert on `job_id` (§B) |
| **Backpressure** | All queues | Streams `BLOCK` + bounded concurrency |
| **Health/readiness probes** | K8s | liveness + readiness; readiness false while ES sync warming |
| **Outbox + retry** | PG→ES sync | Durable events; sync survives ES downtime and drains on recovery |

> **Interview gold (the PostGIS fallback):** *"My favorite resilience detail: if Elasticsearch dies, I fall back to a PostGIS radius query in my source-of-truth Postgres. I lose fuzzy text search and it's slower, but the core 'jobs near me' feature stays up. The dual-store gives me a free degradation path."*

---

<a id="sd"></a>
## §D. Mitigation Strategies

**Insert as §8.6.**

| Risk | Mitigation |
|---|---|
| **PG↔ES divergence** | Outbox pattern, not dual writes; idempotent ES upsert on `job_id`; periodic reconciliation job |
| **Double-texting users** | Idempotent WhatsApp dedup `(user_id, job_id)` + DB UNIQUE backstop (§B.4) |
| **Resume PII leakage** | Encrypt resumes at rest; India-resident storage (DPDP Act); redact phone/email in logs; signed-URL access to files |
| **Bad resume extraction** | Eval harness (§E) measures skill-extraction accuracy; low-confidence parses flagged for review |
| **Bad regional translation** | Translation eval set (§E); fall back to English on low confidence; human-reviewed templates for common alerts |
| **LLM cost runaway** | Mid-tier model for extraction; self-hosted embeddings; cache parses by file hash; daily token budget |
| **Twilio/WhatsApp cost & spam** | Rate-limit alerts per user (max N/day); user opt-in + opt-out; dedup |
| **Fake/scam job postings** | Recruiter verification (OTP + GST/phone); fraud-signal classifier (§G); report button |
| **Geo spoofing / bad coordinates** | Validate lat/lng ranges; clamp radius; reject coordinates outside India bounding box |
| **ES query injection / heavy queries** | Parameterized queries; cap radius + result size; query timeout |
| **Flaky Tier-2/3 networks** | Offline-first PWA (§G); idempotent retries; small payloads |
| **DB connection exhaustion** | HikariCP bounded pool; PgBouncer; separate replica pool for search fallback |
| **Demo fails at interview** | Pre-recorded video; seeded jobs + a demo resume that always parses cleanly |

---

<a id="se"></a>
## §E. DevOps / MLOps Wrapper

**Insert as §11.5. Biggest AI gap: three AI components, zero quality measurement.**

You have **three** things that can silently get worse, and each needs an eval:

### E.1 Resume-extraction eval (the headline MLOps piece)
The question you'll be asked: *"How do you know the resume parser is accurate?"* Answer with numbers.
- A labeled set of ~30 resumes (incl. Hindi/English-mixed, photo-of-resume, informal titles like "dukaan pe kaam") with the **expected extracted fields** (skills, total experience, city).
- Metrics: **skill-extraction F1** (precision + recall on the skills set), **field accuracy** (city/experience correct?), **JSON validity rate**.
- Runs in CI; fails if skill-F1 drops >5 points. (Starter code provided separately.)

> **Interview gold:** *"I measure my resume parser — skill-extraction F1 on a 30-resume benchmark that includes Hindi/English-mixed and photo resumes, plus the informal-title mapping like 'dukaan pe kaam' → 'Retail Sales Assistant'. It runs in CI so a prompt change that quietly got worse can't merge."*

### E.2 Match-quality eval (vector search)
Does the vector match return *relevant* jobs? A labeled set of (candidate profile → relevant job IDs); measure **recall@10** and **MRR**. Compare embedding models (bge-m3 vs alternatives) on this set — that's a real "I chose my embedding model with data" talking point.

### E.3 Translation-quality eval (Bharat-critical)
Regional-language alerts can be wrong in ways you can't read. A small reviewed set of (English alert → expected Hindi/Tamil/etc.); spot-check with native-speaker review or a translation-quality metric. Fall back to English when confidence is low. **This is a product-safety issue** — a garbled job alert in someone's mother tongue erodes trust.

### E.4 LLM observability (Langfuse)
Trace every parse: model, prompt version, tokens, cost, latency, validity. Panels: cost per parse, p95 parse latency, JSON-validity rate, parses per model.

### E.5 Prompt versioning + CI gate
Extraction prompt as a versioned file (`prompts/resume_parse_v3.txt`); each parse logs its version; new versions must beat old on the eval before shipping. CI gate fails on skill-F1 regression >5pts or cost regression >20%.

### E.6 Feedback flywheel
When a candidate corrects their auto-parsed profile, that correction is gold-labeled data → add to the eval set; recurring miss patterns → tune the prompt.

---

<a id="sf"></a>
## §F. Deployment Strategies

**Insert into §9.**

| Strategy | How | When here |
|---|---|---|
| **Rolling update** (default K8s) | `maxSurge=1, maxUnavailable=0` | Demo default — zero downtime |
| **Blue-green** | v2 fleet beside v1; flip LB | Risky changes (ES mapping change, schema change) — instant rollback |
| **Canary** | 5%→25%→100%, watch error rate + parse quality | Prompt/model/embedding changes — canary on **extraction quality**, not just HTTP errors |

**Elasticsearch reindex strategy (zero-downtime):** never mutate a live index mapping. Create `jobs_v2` with the new mapping, reindex from `jobs_v1`, then atomically flip an **index alias** `jobs → jobs_v2`. Searches never see downtime. *Say "alias flip" — it's the phrase that signals you've done a real ES migration.*

**DB migrations:** Flyway/Liquibase with **expand-contract** so rolling updates stay safe with schema changes.

**WebSocket/long-poll draining + PWA:** `terminationGracePeriodSeconds` for graceful drain; the PWA's offline queue tolerates a brief backend blip during deploys.

> **Interview gold:** *"For an Elasticsearch mapping change I reindex into a new index and flip an alias atomically — searches never go down. For a prompt or embedding change I canary on extraction quality via my eval signal, not just HTTP 5xx, because a model can get faster but less accurate."*

---

<a id="sg"></a>
## §G. Bharat Differentiator Features

**These are what make interviewers at CRED/PhonePe/OkCredit remember you.** Build 1–2; write the rest into `docs/future-work/`.

### 🟢 G1 — WhatsApp-First Application Flow (the killer Bharat insight — BUILD THIS)
Tier-2/3 users live in WhatsApp, not apps. Let a candidate **apply to a job entirely via WhatsApp conversation** — no app install, no resume upload. The bot asks for name, skills, experience in their language; your resume-parser pipeline structures the chat into a profile; they're applied. Uses Twilio's WhatsApp inbound webhooks (you already have outbound).

> *"My differentiator is WhatsApp-first apply. A daily-wage worker in Kanpur with a ₹5,000 phone doesn't install apps — but they're on WhatsApp all day. So they apply by chatting in Hindi, and my pipeline structures the conversation into a candidate profile. That's building for the next billion, not porting a city product to a village."*

### 🟢 G2 — Offline-First PWA for Flaky Networks (BUILD THIS — on-theme)
Tier-2/3 networks are 2G/3G and intermittent. A PWA with a service worker that caches the job feed, queues applications offline, and syncs when connectivity returns. Lighthouse PWA score in the README. This *is* the Bharat product-thinking signal.

### 🟢 G3 — Resume-Extraction Eval Harness (BUILD THIS)
Covered in §E.1 — both MLOps practice and differentiator. Almost no fresher measures AI quality.

### 🟡 G4 — Hybrid Search (BM25 + Vector, Reciprocal Rank Fusion)
Combine Elasticsearch BM25 keyword search with pgvector semantic match via reciprocal rank fusion. Catches both "exact keyword" and "semantically similar" jobs. A strong search-quality talking point measurable via §E.2.

### 🟡 G5 — Transliteration + Voice Search (low-literacy access)
Let users type Hindi in Latin script ("naukri chahiye") or **speak** their search (Web Speech API → Bhashini ASR). Low-literacy users are a huge Bharat segment your competitors ignore.

### 🟠 G6 — Fake-Job / Fraud Detection (write up or light build)
Indian job boards are plagued by scam postings ("pay ₹500 registration"). A light classifier on posting text + recruiter signals (unverified phone, payment-request keywords) flags suspicious jobs. Real product-safety thinking.

### 🟠 G7 — Skill-Gap Recommendations (write up)
"You're missing *Tally* for this accounting job — here's a free 2-hour course." Turns a job board into a career tool. Uses the same skill embeddings.

### 🟠 G8 — Phone-OTP-Only Auth + DPDP Compliance (write up)
No email — Bharat is phone-first. OTP login. Plus India-resident data storage for DPDP Act compliance. Product + compliance maturity.

| If you have… | Build |
|---|---|
| 1 extra week | G3 (eval) + G2 (offline PWA) |
| 2 extra weeks | + G1 (WhatsApp-first apply) — the headline |
| 3 extra weeks | + G4 (hybrid search) |
| Writing only | G5–G8 as `docs/future-work/` + ADRs |

---

<a id="sh"></a>
## §H. Extra Interview Q&A (for the new material)

**Q. Is your system CP or AP?** Both. Postgres (source of truth) is CP — posting a job is one ACID transaction. Elasticsearch (search index) is AP — searches are fast/available even if the index lags by seconds. Opposite consistency needs for "post" vs "search."

**Q. How do Postgres and Elasticsearch stay consistent?** Transactional outbox, not dual writes. The job and an outbox event commit in one Postgres transaction; a sync worker drains the outbox into ES with retries. At-least-once indexing + idempotent upsert on `job_id` = effectively-once. Dual writes would risk silent divergence if one write failed.

**Q. What happens if Elasticsearch goes down?** Search degrades gracefully to a PostGIS `ST_DWithin` query in my source-of-truth Postgres — slower, no fuzzy text, but the core "jobs near me" stays up. The dual-store gives a free fallback.

**Q. A user gets the same job alert twice — how do you prevent it?** Idempotent WhatsApp send: dedup on `(user_id, job_id)` via Redis SETNX with a 7-day TTL, plus a DB UNIQUE constraint backstop. Notification jobs are at-least-once; the idempotent send makes the effect effectively-once.

**Q. Why did you move off GPT-4, and why a mid-tier model?** GPT-4 is dated; but more importantly, resume extraction is high-volume structured extraction, which a mid-tier model does accurately at ~10× lower cost. I reserve frontier models for ambiguous resumes. Each AI job (extraction, vision, embedding, translation) is behind an adapter and uses the right model.

**Q. Why Bhashini over an LLM for translation?** Bhashini is India's government translation stack for 22 Indian languages — more accurate on low-resource languages like Bhojpuri, cheaper, and the credible Bharat choice. A garbled alert in someone's mother tongue destroys trust, so translation quality is a product-safety issue I measure with an eval set.

**Q. How do you know your resume parser is accurate?** A 30-resume benchmark (Hindi/English-mixed, photo resumes, informal titles) with expected fields; I measure skill-extraction F1 and field accuracy, gated in CI. It catches a prompt change that quietly got worse.

**Q. How do you deploy an Elasticsearch mapping change with zero downtime?** Reindex into `jobs_v2`, then atomically flip the `jobs` alias. Searches never see the switch.

**Q. Biggest differentiator?** WhatsApp-first apply — candidates apply by chatting in their language, no app install. Building for how Bharat actually uses phones.

---

## Summary: bolt-in priority

1. **§0 model update** — replace `GPT-4`/`GPT-4 Vision`, add the per-task adapter + Bhashini. (Editing.)
2. **§B Availability & Consistency (outbox + idempotent WhatsApp + PostGIS fallback)** — the new requirement *and* the real correctness fix. **Must-have.**
3. **§E MLOps + §E.1 resume eval** — turns "I called GPT" into "I measure three AI components." Highest interview ROI.
4. **§C/§D/§F** — completes the checklist, mostly writing.
5. **§G1 WhatsApp-first apply** + **§G2 offline PWA** — the Bharat headline differentiators.
6. **§A architecture + §H Q&A** — polish.

Your original was ~80% there. These take it to complete *and* Bharat-differentiated.
