# NaukriNearby — Hyperlocal Job Board for India's Tier-2/3 Cities
## Complete Implementation Plan v2 (Master) — Junior Full-Stack Developer Interview (2026)

> **This is the merged master document.** It folds the v2 supplement (model update, detailed system architecture, availability & consistency patterns, resilience, mitigation, MLOps wrapper, deployment strategies, Bharat differentiators) into the original plan, renumbered into one clean flow.

---

## Table of Contents

1. Project Overview & Interview Hook
2. Model Choice — Current Models + Bhashini *(new in v2)*
3. Tech Stack — Every Choice Justified
4. MVP Blueprint — 7-Week Build Plan
5. High-Level Design (HLD)
6. Detailed System Architecture (Infra Topology) *(new in v2)*
7. Low-Level Design (LLD)
8. Database Design & Choice
9. Availability & Consistency Patterns *(new in v2)*
10. Caching & Messaging — Redis vs Kafka vs RabbitMQ
11. Design Patterns Used
12. Resilience Patterns (Consolidated) *(new in v2)*
13. Mitigation Strategies *(new in v2)*
14. Docker & Kubernetes
15. Deployment Strategies — Rolling / Blue-Green / Canary *(new in v2)*
16. CI/CD Pipeline — GitHub Actions
17. Monitoring & Observability
18. DevOps / MLOps Wrapper *(new in v2)*
19. Bharat Differentiator Features *(new in v2)*
20. README Blueprint
21. Interview Prep — Questions & Answers
22. Deployment Checklist — Go Live
23. Final Word

---

## 1. Project Overview & Interview Hook

**Project Name:** NaukriNearby

**One-liner:** A hyperlocal job board purpose-built for India's Tier-2/3 cities — employers post geotagged jobs, candidates upload resumes that an LLM auto-parses into skill vectors, Elasticsearch matches candidates to nearby jobs within a configurable radius, and job alerts arrive on WhatsApp in the candidate's regional language.

**Interview Hook (memorize this):**

> "I built a hyperlocal job board for Bharat's next-billion users. Employers post geotagged jobs — a kirana store in Indore posts for a delivery boy and only candidates within 5 km see it. I parse vernacular resumes into structured skill vectors with an LLM behind a provider-agnostic adapter, store them in pgvector for semantic matching, and send WhatsApp alerts in Hindi, Tamil, or Telugu via Twilio — translated with Bhashini, India's government language stack. Geo-search uses Elasticsearch geo_distance with a PostGIS fallback, and I keep Postgres and Elasticsearch consistent with the transactional outbox pattern. I deployed it with Docker Compose, built a CI/CD pipeline that gates merges on a resume-extraction eval, and documented a scaling decision to separate notification workers from the API."

**Why interviewers love this:**
- Shows you understand India's real market (Tier-2/3, WhatsApp-first, multilingual) — CRED, PhonePe, Meesho, OkCredit interviewers connect immediately
- Geospatial engineering (PostGIS + Elasticsearch geo queries)
- AI integration (LLM resume parsing + vector similarity search) **measured with an eval harness**
- External API orchestration (Twilio WhatsApp + Bhashini)
- Event-driven notifications + reliable dual-store consistency (outbox)
- Full DevOps + MLOps maturity (Docker, CI/CD, monitoring, eval gate, documented scaling decision)

**Target Companies (Bangalore):** CRED, PhonePe, OkCredit (building-for-Bharat); Meesho, Udaan, Dunzo (hyperlocal); Razorpay, Zerodha (engineering culture); Flipkart, Swiggy (geospatial + scale); Atlassian India, Thoughtworks (clean architecture + DevOps).

---

## 2. Model Choice — Current Models + Bhashini

**The original brief said "GPT-4." For a 2026 project that's dated.** But more importantly, this project has **three distinct AI jobs**, and the right model differs for each. Hardcoding one vendor is the junior move — put each behind an adapter and pick per task.

| AI job | Recommended | Why |
|---|---|---|
| **Resume → structured JSON extraction** | A current **mid-tier** model (GPT-5-mini-class / Claude Haiku-class) behind an adapter | Structured extraction with a tight schema is not the hardest task; mid-tier is accurate and ~10× cheaper than frontier. Resume parsing is high-volume → cost matters. |
| **Resume image → text (OCR/Vision)** | A current vision model OR dedicated OCR (Google Vision / Tesseract) | Indian resumes are often photos with mixed Hindi/English; vision handles layout, dedicated OCR is cheaper at volume. |
| **Skill/job semantic match** | An **embedding** model — open `bge-m3` / `gte-multilingual`, or a hosted one | `bge-m3` is multilingual (critical for Hindi/regional skill text) and self-hostable → zero per-call cost. |
| **Regional-language translation** | **Bhashini** (Government of India's translation stack for 22 Indian languages) — *not* a general LLM | More accurate on low-resource languages (Bhojpuri, Maithili), cheaper, and the credible Bharat choice. |

> **Bharat interview gold:** *"For regional-language alerts I use Bhashini — the Government of India's translation stack for 22 Indian languages — not a general LLM. It's more accurate on low-resource languages, it's the credible choice for a Bharat product, and it signals I researched India-specific tooling. A garbled job alert in someone's mother tongue destroys trust, so translation quality is a product-safety issue I measure (§18)."*

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
    temperature: 0.0                            # deterministic extraction
  embedding:
    model: ${EMBEDDING_MODEL:bge-m3}            # multilingual, self-hostable
    dims: ${EMBEDDING_DIMS:1024}                # MUST match the pgvector column dimension
  translation:
    provider: ${TRANSLATION_PROVIDER:bhashini}  # bhashini | google
```

> **Cost interview gold:** *"Resume parsing is my highest-volume LLM call, so I deliberately use a mid-tier model — structured extraction with a strict JSON schema doesn't need a frontier model. I reserve any frontier model for genuinely ambiguous resumes, and I use a self-hostable multilingual embedding model (bge-m3) so the vector-match step has zero per-call cost."*

> **Note on embedding dimensions:** the pgvector column dimension must equal your embedding model's output (1024 for `bge-m3`, 1536 for `text-embedding-3-small`). Pick one and keep the schema, config, and index consistent.

---

## 3. Tech Stack — Every Choice Justified

### Core Stack

| Layer | Technology | Why This (Interview Answer) |
|---|---|---|
| **Frontend** | Next.js 16 (App Router) + React 19.2 + TypeScript | SSR for SEO (job listings must be Google-indexed). Server components for faster loads on slow Tier-2/3 networks. TS prevents bugs at scale. Next.js 16 (current stable; 14 is EOL) on Node.js 24 LTS, Turbopack default. |
| **UI Library** | Tailwind CSS + shadcn/ui | Rapid prototyping; accessible, production-grade components; no CSS bloat. |
| **Maps** | Leaflet.js (OpenStreetMap) | Free (no Google Maps billing). Excellent India coverage. 42KB vs Google Maps 200KB+ — crucial for slow networks. |
| **Backend** | Spring Boot 4.0.6 (Java 21) | Dominant in Bangalore product companies. Current Spring generation (Framework 7): virtual threads for concurrent external API calls, `@HttpExchange` HTTP Service Clients for the provider adapters (§2), native `@Retryable`/`@ConcurrencyLimit` + Resilience4j for resilience (§12). Java 21 LTS is fully supported on 4.0.6 (Java 17–26 range). |
| **Search Engine** | Elasticsearch 8.x | geo_distance + full-text with analyzers for Hindi/regional text; relevance scoring blends proximity + skill match. |
| **Primary DB** | PostgreSQL 16 + PostGIS + pgvector | See §8. Relational integrity + geospatial + vectors in one DB. |
| **Vector Search** | pgvector (PostgreSQL extension) | Stores skill embeddings; cosine similarity search. No separate vector DB at MVP scale. |
| **Cache + Queue** | Redis 7 | OTP, session/search cache, rate limiting, Redis Streams queues. See §10. |
| **AI / NLP** | Current LLM behind an adapter (mid-tier for extraction) — see §2 | Resume parsing, skill extraction from vernacular resumes, embedding generation. |
| **Translation** | **Bhashini** (primary), Google Cloud Translation (fallback) | Regional-language job alerts in 22 Indian languages. |
| **Notifications** | Twilio WhatsApp Business API | 500M+ Indians on WhatsApp; SMS open rates ~20% vs WhatsApp 90%+. |
| **LLM Observability** | Langfuse (self-hostable) | Trace every parse: prompt version, tokens, cost, latency. See §18. |
| **Containerization** | Docker + Docker Compose | Multi-service orchestration. |
| **CI/CD** | GitHub Actions | Free for public repos; native Docker build/push; deploy to EC2 or Railway. |
| **Monitoring** | Prometheus + Grafana | Actuator metrics: search latency, delivery rate, parse time. |

### Why NOT These Alternatives (Interview Ammo)

| Rejected Option | Why |
|---|---|
| Express.js/Node backend | Spring Boot preferred by Bangalore companies; virtual threads outperform Node's event loop for concurrent external calls. |
| **Spring Boot 3.x** | 3.3/3.4 are EOL and 3.5 (the last 3.x) loses OSS support on 2026-06-30 — starting a brand-new 2026 build on a dying branch is a weak interview signal. Spring Boot 4.0.6 (Apr 2026, 6th patch — stable) runs on Java 21 (Java 17–26) and is supported into 2027, with Framework 7 features (HTTP Service Clients, native resilience, OpenTelemetry starter) that map directly onto this design. |
| MongoDB | Deeply relational data (users → resumes → applications → jobs). PostGIS beats 2dsphere; pgvector removes the need for a separate vector DB. |
| Pinecone/Weaviate | Separate vector DB is overengineering for MVP. pgvector handles ~1M vectors efficiently. Migrate later if needed. |
| Firebase/Supabase | No Elasticsearch integration; limited geospatial; vendor lock-in. |
| React SPA | Job pages MUST be server-rendered for SEO. Next.js SSR solves this natively. |
| Algolia | Expensive at scale; no native geo_distance with custom scoring. |
| Google Maps API | Costs after free tier. OSM + Leaflet is free with great India coverage. |
| **Hardcoding GPT-4** | Vendor lock-in; the per-task adapter (§2) keeps each model swappable. |
| **General LLM for translation** | Weaker on low-resource Indian languages than Bhashini; more expensive. |

---

## 4. MVP Blueprint — 7-Week Build Plan

### MVP Scope (What to Build)

**Must Have (MVP):**
- Employer registration & job posting (location pin on map)
- Candidate registration & resume upload (PDF/image)
- LLM auto-parses resume → extracts skills, experience, location
- Skill embeddings stored in pgvector
- Hyperlocal job search: "jobs within X km of my location"
- Elasticsearch geo_distance + skill relevance search
- **PG→ES sync via the outbox pattern** (not dual writes — §9)
- Job detail page (SSR for SEO)
- One-click "Apply"
- **Idempotent WhatsApp job alerts** in the candidate's language (§9)
- Employer dashboard (posted jobs, applications)
- Candidate dashboard (applied jobs, saved jobs, alert preferences)

**Nice to Have (Post-MVP):** admin moderation, employer GST/Aadhaar verification, video resume, interview scheduling, salary benchmarking, offline PWA, voice search.

### Week-by-Week Schedule

**Week 1 — Foundation & Auth:** Spring Boot + PostgreSQL (PostGIS + pgvector) via Docker Compose; phone+OTP auth (Twilio Verify); JWT + Redis session; Next.js scaffold. → Users register and see dashboard shell.

**Week 2 — Employer: Job Posting:** Job model with PostGIS GEOGRAPHY point; posting form with Leaflet map; geocoding; SSR listing/detail pages; **write job + outbox event in one transaction** (§9). → Employer posts geotagged job, visible publicly.

**Week 3 — Candidate: Resume Upload & AI Parsing:** upload endpoint + MinIO; LLM resume parsing via adapter (§2) → structured fields; skill embedding (bge-m3) → pgvector; candidate profile page; **set up the resume-extraction eval harness now, not later** (§18). → Upload resume → AI extracts data → profile auto-populated.

**Week 4 — Search & Matching:** Elasticsearch mapping with geo_point; **outbox sync worker drains jobs into ES**; geo-search API; relevance scoring (geo + skill + recency); pgvector match; results page with map. → Candidates search hyperlocal jobs on a map.

**Week 5 — Applications & WhatsApp Notifications:** Application model + apply flow; employer dashboard; **idempotent** WhatsApp pipeline (translate via Bhashini → send via Twilio, dedup on `(user_id, job_id)` — §9); notification preferences; scheduler. → Apply, see applications, WhatsApp alerts work.

**Week 6 — Dashboard & Polish:** candidate + employer dashboards; SEO (meta + JSON-LD JobPosting); error/loading/empty states; mobile responsiveness. → Complete, polished app.

**Week 7 — DevOps, MLOps & Deployment:** Dockerfiles; docker-compose; GitHub Actions CI/CD **with resume-extraction eval gate** (§16); Prometheus + Grafana + Langfuse; README + diagram; deploy publicly; demo video. → Live deployed app with CI/CD, monitoring, eval gate.

---

## 5. High-Level Design (HLD)

### Architecture Overview

```
┌─────────────────┐                      ┌──────────────────────────────┐
│   Candidate      │                      │   Employer                   │
│   (Mobile/Web)   │                      │   (Web Dashboard)            │
└────────┬─────────┘                      └──────────────┬───────────────┘
         │  HTTPS (SSR + API)                            │ HTTPS (API)
┌────────▼───────────────────────────────────────────────▼───────────────┐
│                        NEXT.JS FRONTEND (SSR + Client)                 │
│  Job Search+Map · Job Detail(SSR/SEO) · Resume Upload · Dashboards     │
└────────────────────────────┬───────────────────────────────────────────┘
                       REST API + SSR data fetching
┌────────────────────────────▼───────────────────────────────────────────┐
│                      SPRING BOOT BACKEND                               │
│  Auth · Job Service(CRUD+geo) · Search Service · Notification Service  │
│  Resume Parser(LLM) · Matching Engine(pgvector) · Application · Trans. │
└───────┬────────────────┬───────────────┬──────────────────┬────────────┘
        │                │               │                  │
   ┌────▼────┐    ┌──────▼──────┐  ┌─────▼─────┐    ┌──────▼───────┐
   │PostgreSQL│    │Elasticsearch│  │   Redis   │    │ External APIs│
   │+ PostGIS │    │   8.x      │  │   7.x     │    │ - LLM(adapter)│
   │+ pgvector│    │(geo+text   │  │(cache +   │    │ - Twilio     │
   │SOURCE OF │    │  INDEX)    │  │ queue +   │    │ - Bhashini   │
   │ TRUTH    │◄───┤eventually  │  │ rate limit)│    │ - MinIO (S3) │
   └──────────┘out-│consistent  │  └───────────┘    └──────────────┘
            box   └─────────────┘
```

### Core Request Flows

**Flow 1: Candidate Searches for Nearby Jobs** — browser shares location → `GET /api/jobs/search?lat&lng&radius&q` → Spring Boot → Elasticsearch geo_distance + full-text + scoring → Next.js renders map + cards → SSR job detail page (Google-indexable).

**Flow 2: Candidate Uploads Resume → AI Parsing** — file → MinIO → Resume Parser calls the LLM (adapter) → structured JSON (handles Hindi/English mix) → candidate confirms → skills → embedding → pgvector → profile auto-populated.

**Flow 3: WhatsApp Job Alert** — new job posted → matcher finds candidates within radius with cosine similarity > threshold → enqueue in Redis Streams → worker composes message, **dedups on `(user_id, job_id)`**, translates via Bhashini, sends via Twilio → delivery tracked via webhook.

**Flow 4: AI-Powered Job Matching (Vector Search)** — candidate skill vector + job requirement vector → pgvector cosine similarity combined with PostGIS distance filter → hyperlocal + skill-relevant recommendations.

---

## 6. Detailed System Architecture (Infra Topology)

The HLD shows logic flow; this shows deployment topology + the dual-store.

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
        ┌────────────────────────────────┼──────────────────────────┼──────────────┐
        ▼                ▼                ▼                          ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐        ┌─────────────────┐ ┌──────────┐
│ PostgreSQL   │ │ Elasticsearch│ │ Redis        │        │ LLM / Embedding │ │ Bhashini │
│ +PostGIS     │ │ (search      │ │ • cache      │        │ (adapter) +     │ │ + Twilio │
│ +pgvector    │◄┤  INDEX, geo) │ │ • Streams    │        │ Langfuse trace  │ │ WhatsApp │
│ SOURCE OF    │ │ eventually   │ │ • idempotency│        └─────────────────┘ └──────────┘
│ TRUTH (CP)   │ │ consistent(AP)│ │ • dedup      │
└──────┬───────┘ └──────▲───────┘ └──────────────┘
       │  outbox events  │
       └─────────────────┘   (PG→ES sync via outbox pattern — §9)

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

**Key decision:** *Postgres is the source of truth (CP); Elasticsearch is a derived search index (AP), kept eventually consistent via an outbox pattern (§9), not dual writes. Workers are split by job type so resume-parsing load can't starve WhatsApp delivery.*

---

## 7. Low-Level Design (LLD)

### 7.1 Backend Package Structure

```
src/main/java/com/naukrinearby/
├── config/         # SecurityConfig, ElasticsearchConfig, RedisConfig,
│                   # LlmConfig (adapter selection), TwilioConfig, MinIOConfig,
│                   # TranslationConfig (Bhashini), SchedulerConfig
├── controller/     # Auth, Job, Candidate, Resume, Application, Employer,
│                   # Notification, Webhook (Twilio delivery status)
├── service/        # AuthService, JobService (CRUD + outbox write), SearchService,
│                   # ResumeParserService (LLM adapter), EmbeddingService,
│                   # MatchingService, ApplicationService, NotificationService,
│                   # WhatsAppService, TranslationService (Bhashini),
│                   # GeocodingService, FileStorageService, NotificationScheduler,
│                   # OutboxSyncWorker (PG→ES), IdempotencyService
├── model/
│   ├── entity/     # User, Candidate, Employer, Job, CandidateProfile,
│   │               # Application, NotificationPreference, NotificationLog, OutboxEvent
│   ├── dto/        # JobSearchRequest/Response, ResumeParseResult, MatchResult,
│   │               # WhatsAppMessage, DashboardStatsDTO
│   ├── enums/      # UserRole, JobCategory, JobStatus, ApplicationStatus, Language, NotificationChannel
│   └── elasticsearch/  # JobDocument
├── repository/     # User, Job, CandidateProfile (pgvector), Application,
│                   # NotificationLog, OutboxRepository, elasticsearch/JobSearchRepository
├── exception/      # GlobalExceptionHandler, ResumeParseException, NotificationException, SearchException
└── util/           # GeoUtils, PromptTemplates, PhoneNumberValidator, SlugGenerator
```

### 7.2 Frontend Structure (Next.js 16 App Router)

```
src/
├── app/
│   ├── page.tsx                  # Landing (hero + search)
│   ├── (auth)/login · register   # Phone + OTP
│   ├── jobs/page.tsx             # Search results + map
│   ├── jobs/[slug]/page.tsx      # Job detail (SSR for SEO)
│   ├── candidate/                # dashboard · profile · resume/upload · alerts
│   └── employer/                 # dashboard · jobs/new · jobs/[id]/applications
├── components/
│   ├── jobs/    # JobCard, JobFilters, JobMap (Leaflet), LocationPicker, DistanceBadge
│   ├── resume/  # ResumeUploader (drag-drop + camera), ParsedProfilePreview, SkillTag
│   ├── search/  # SearchBar, SearchResults (list+map toggle), RadiusSlider
│   ├── notifications/  # LanguageSelector, AlertPreferences
│   └── common/  # OTPInput, LoadingSkeleton, EmptyState, ErrorBoundary
├── hooks/       # useGeolocation, useJobSearch, useAuth, useResumeUpload
├── services/    # api, authService, jobService, searchService, candidateService
├── lib/         # seo (meta + JSON-LD), constants
└── types/
```

### 7.3 Key API Endpoints

```
AUTH          POST /api/auth/send-otp · /verify-otp · GET /api/auth/me · POST /logout
JOBS          POST /api/jobs · PUT /api/jobs/{id} · PATCH /{id}/status · GET /api/employer/jobs
SEARCH        GET /api/jobs/search?lat&lng&radius&q&category&sort · GET /api/jobs/{slug}
              GET /api/jobs/recommended
CANDIDATE     GET/PUT /api/candidate/profile · POST /api/resumes/upload · GET /{id}/parse-status
APPLICATIONS  POST /api/applications · GET /api/candidate/applications
              GET /api/employer/jobs/{id}/applicants · PATCH /api/applications/{id}/status
NOTIFICATIONS GET/PUT /api/notifications/preferences · POST /api/webhooks/twilio
DASHBOARD     GET /api/candidate/dashboard/stats · GET /api/employer/dashboard/stats
```

### 7.4 Core Service — Resume Parsing (Pseudocode)

```java
@Service
@RequiredArgsConstructor
public class ResumeParserService {
    private final LlmExtractionProvider llm;          // adapter (§2), not OpenAI directly
    private final VisionProvider vision;              // adapter
    private final EmbeddingService embeddingService;
    private final CandidateProfileRepository profileRepo;
    private final FileStorageService storageService;

    @Async  // virtual thread
    public CompletableFuture<ResumeParseResult> parseResume(Long candidateId, String fileKey,
                                                            String promptVersion) {
        byte[] fileBytes = storageService.download(fileKey);

        String resumeText = isImage(fileKey)
            ? vision.extractText(fileBytes,
                "Extract all text from this Indian resume image. " +
                "Include Hindi/regional text transliterated to English.")
            : extractTextFromPDF(fileBytes);

        // Structured extraction via the LLM adapter (mid-tier model, temp 0).
        // System prompt: "You are an expert Indian HR recruiter. Return ONLY valid JSON:
        //   {name, phone, email, skills[], experience[{title,company,months}],
        //    education[], city, state, languages_spoken[], total_experience_months}.
        //   Handle Hindi+English mixed resumes. Map informal titles
        //   (e.g., 'dukaan pe kaam' -> 'Retail Sales Assistant')."
        ResumeParseResult parsed = llm.extractStructured(resumeText, ResumeParseResult.class, promptVersion);

        float[] embedding = embeddingService.generateEmbedding(String.join(", ", parsed.getSkills()));

        profileRepo.save(CandidateProfile.builder()
            .candidateId(candidateId).name(parsed.getName()).phone(parsed.getPhone())
            .skills(parsed.getSkills()).experience(parsed.getExperience())
            .city(parsed.getCity()).state(parsed.getState())
            .skillEmbedding(embedding).resumeFileKey(fileKey).build());

        return CompletableFuture.completedFuture(parsed);
    }
}
```

### 7.5 Core Service — Elasticsearch Geo Search

```java
@Service
@RequiredArgsConstructor
public class SearchService {
    private final ElasticsearchClient esClient;
    private final RedisTemplate<String, String> redisTemplate;

    public SearchResponse searchJobs(JobSearchRequest req) {
        String cacheKey = "search:" + req.hashCode();
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return deserialize(cached);

        SearchRequest searchRequest = SearchRequest.of(s -> s.index("jobs")
            .query(q -> q.bool(b -> {
                b.filter(f -> f.geoDistance(g -> g.field("location")
                    .distance(req.getRadius())
                    .location(l -> l.latlon(ll -> ll.lat(req.getLat()).lon(req.getLng())))));
                if (req.getQuery() != null)
                    b.must(m -> m.multiMatch(mm -> mm
                        .fields("title^3", "description", "skills^2")
                        .query(req.getQuery()).fuzziness("AUTO")));
                if (req.getCategory() != null)
                    b.filter(f -> f.term(t -> t.field("category").value(req.getCategory())));
                b.filter(f -> f.term(t -> t.field("status").value("ACTIVE")));
                return b;
            }))
            .sort(sort -> sort.geoDistance(g -> g.field("location")
                .location(l -> l.latlon(ll -> ll.lat(req.getLat()).lon(req.getLng())))
                .order(SortOrder.Asc)))
            .size(20));

        var response = esClient.search(searchRequest, JobDocument.class);
        redisTemplate.opsForValue().set(cacheKey, serialize(response), Duration.ofMinutes(5));
        return mapToResponse(response);
    }
}
```
*(For the ES-down fallback to a PostGIS `ST_DWithin` query, see §9.6.)*

### 7.6 Core Service — WhatsApp Notification Pipeline (idempotent)

```java
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final WhatsAppService whatsAppService;
    private final TranslationService translationService;   // Bhashini
    private final MatchingService matchingService;
    private final NotificationLogRepository logRepo;
    private final RedisTemplate<String, String> redisTemplate;

    public void notifyMatchingCandidates(Job job) {
        var matches = matchingService.findCandidatesForJob(job.getId(), job.getLocation(),
            job.getRadiusKm(), job.getRequirementVector(), 0.6);

        for (var match : matches) {
            // Daily rate limit: max 3 WhatsApp messages per candidate per day.
            String rateKey = "notify:daily:" + match.getCandidateId();
            Long count = redisTemplate.opsForValue().increment(rateKey);
            if (count == 1) redisTemplate.expire(rateKey, Duration.ofDays(1));
            if (count > 3) continue;

            redisTemplate.opsForStream().add("notification-jobs", Map.of(
                "candidateId", match.getCandidateId().toString(),
                "jobId", job.getId().toString(), "phone", match.getPhone(),
                "language", match.getPreferredLanguage().getCode(),
                "distance", String.format("%.1f", match.getDistanceKm()),
                "jobTitle", job.getTitle(), "companyName", job.getCompanyName()));
        }
    }

    @Scheduled(fixedDelay = 1000)
    public void processNotificationQueue() {
        var messages = redisTemplate.opsForStream().read(
            Consumer.from("notifiers", "worker-1"),
            StreamReadOptions.empty().count(10).block(Duration.ofSeconds(5)),
            StreamOffset.create("notification-jobs", ReadOffset.lastConsumed()));
        if (messages == null) return;

        for (var msg : messages) {
            try {
                Map<Object, Object> d = msg.getValue();
                String candidateId = (String) d.get("candidateId");
                String jobId = (String) d.get("jobId");

                // IDEMPOTENCY: never send the same (candidate, job) alert twice (§9.4).
                String dedup = "notif:sent:" + candidateId + ":" + jobId;
                Boolean isNew = redisTemplate.opsForValue().setIfAbsent(dedup, "1", Duration.ofDays(7));
                if (Boolean.FALSE.equals(isNew)) {
                    redisTemplate.opsForStream().acknowledge("notification-jobs", "notifiers", msg.getId());
                    continue;
                }

                String english = String.format(
                    "New job near you! %s at %s, %s km away. Apply: https://naukrinearby.in/jobs/%s",
                    d.get("jobTitle"), d.get("companyName"), d.get("distance"), jobId);
                String lang = (String) d.get("language");
                String text = lang.equals("en") ? english
                    : translationService.translate(english, "en", lang);   // Bhashini

                whatsAppService.sendMessage((String) d.get("phone"), text);
                logRepo.save(NotificationLog.of(candidateId, jobId, "WHATSAPP", "SENT", text));
                redisTemplate.opsForStream().acknowledge("notification-jobs", "notifiers", msg.getId());
            } catch (Exception e) {
                log.error("Notification failed for {}", msg.getId(), e);   // stays pending → retried; DLQ after N (§12)
            }
        }
    }
}
```

---

## 8. Database Design & Choice

### 8.1 Comparison Matrix — Which Database?

| Criteria | PostgreSQL + PostGIS + pgvector | MongoDB | Cassandra | TimescaleDB | CosmosDB |
|---|---|---|---|---|---|
| **Data Model** | Relational + geospatial + vectors | Document | Wide-column | Time-series on PG | Multi-model |
| **Geospatial** | PostGIS — gold standard (polygon, radius, NN) | 2dsphere (decent) | None | Same as PostGIS | Available |
| **Vector Search** | pgvector (cosine/L2, HNSW) | Atlas Vector (limited) | No | No | Preview |
| **Relationships** | Native JOINs, FKs | Manual $lookup | No JOINs | Same as PG | API-dependent |
| **ACID** | Full | Single-doc only | Eventual | Full | Configurable |
| **Operational Cost** | Low (1 DB for all 3) | Medium | High (3+ nodes) | Low | High (cloud-only) |

### VERDICT: PostgreSQL 16 + PostGIS 3.4 + pgvector

1. **One database, three capabilities:** relational + geospatial + vector. Any other choice needs 2–3 databases.
2. **PostGIS is the geospatial gold standard:** "jobs within 5 km" is one `ST_DWithin`; supports Indian district/pincode polygons.
3. **pgvector eliminates a separate vector DB** at MVP scale (<100K candidates, <50ms HNSW search).
4. **ACID for the application workflow:** apply → create Application + bump count + enqueue notification, atomically.
5. **JSONB for flexible AI output:** parsed resume fields stored flexibly, still queryable.

**Why NOT others:** MongoDB loses JOINs + PostGIS + pgvector (3 DBs vs 1); Cassandra has zero geospatial; TimescaleDB's superpower (time-series) isn't our primary query; CosmosDB locks into Azure with RU pricing.

### 8.2 Schema Design (PostgreSQL + PostGIS + pgvector)

```sql
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(15) UNIQUE NOT NULL,
    role VARCHAR(20) NOT NULL,                     -- CANDIDATE, EMPLOYER
    name VARCHAR(200), email VARCHAR(255),
    is_verified BOOLEAN DEFAULT FALSE,
    preferred_language VARCHAR(5) DEFAULT 'en',
    created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_user_phone ON users(phone);

CREATE TABLE employers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    company_name VARCHAR(300) NOT NULL, company_type VARCHAR(50),
    gst_number VARCHAR(20), address TEXT, city VARCHAR(100), state VARCHAR(100),
    location GEOGRAPHY(POINT, 4326),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_employer_location ON employers USING GIST(location);

CREATE TABLE candidate_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(200), phone VARCHAR(15), email VARCHAR(255),
    skills TEXT[] NOT NULL DEFAULT '{}',
    experience JSONB DEFAULT '[]', education JSONB DEFAULT '[]',
    city VARCHAR(100), state VARCHAR(100),
    location GEOGRAPHY(POINT, 4326),
    languages_spoken TEXT[] DEFAULT '{en}',
    total_experience_months INT DEFAULT 0,
    preferred_radius_km INT DEFAULT 10, preferred_categories TEXT[] DEFAULT '{}',
    skill_embedding vector(1024),                  -- dims MUST match embedding model (§2)
    resume_file_key VARCHAR(500), resume_parsed_at TIMESTAMP,
    profile_completeness INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_candidate_location ON candidate_profiles USING GIST(location);
CREATE INDEX idx_candidate_skills ON candidate_profiles USING GIN(skills);
CREATE INDEX idx_candidate_embedding ON candidate_profiles
    USING hnsw(skill_embedding vector_cosine_ops) WITH (m = 16, ef_construction = 64);

CREATE TABLE jobs (
    id BIGSERIAL PRIMARY KEY,
    employer_id BIGINT REFERENCES employers(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL, slug VARCHAR(350) UNIQUE NOT NULL,
    description TEXT, category VARCHAR(50) NOT NULL,
    skills_required TEXT[] DEFAULT '{}',
    salary_min INT, salary_max INT, salary_type VARCHAR(20) DEFAULT 'MONTHLY',
    experience_required_months INT DEFAULT 0,
    location GEOGRAPHY(POINT, 4326), address TEXT,
    city VARCHAR(100) NOT NULL, state VARCHAR(100) NOT NULL, pincode VARCHAR(10),
    radius_km INT DEFAULT 10,
    requirement_vector vector(1024),               -- dims MUST match embedding model (§2)
    status VARCHAR(20) DEFAULT 'ACTIVE',
    version INT NOT NULL DEFAULT 1,                 -- optimistic concurrency / outbox guard (§9)
    is_whatsapp_enabled BOOLEAN DEFAULT TRUE,
    application_count INT DEFAULT 0, expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_job_location ON jobs USING GIST(location);
CREATE INDEX idx_job_status ON jobs(status);
CREATE INDEX idx_job_city ON jobs(city);
CREATE INDEX idx_job_embedding ON jobs
    USING hnsw(requirement_vector vector_cosine_ops) WITH (m = 16, ef_construction = 64);

CREATE TABLE applications (
    id BIGSERIAL PRIMARY KEY,
    job_id BIGINT REFERENCES jobs(id) ON DELETE CASCADE,
    candidate_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) DEFAULT 'APPLIED', cover_note TEXT,
    applied_at TIMESTAMP DEFAULT NOW(), updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(job_id, candidate_id)
);
CREATE INDEX idx_app_job ON applications(job_id);
CREATE INDEX idx_app_candidate ON applications(candidate_id);

CREATE TABLE notification_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    channel VARCHAR(20) DEFAULT 'WHATSAPP', language VARCHAR(5) DEFAULT 'en',
    radius_km INT DEFAULT 10, categories TEXT[] DEFAULT '{}',
    frequency VARCHAR(20) DEFAULT 'INSTANT', is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE notification_logs (
    id BIGSERIAL PRIMARY KEY,
    candidate_id BIGINT REFERENCES users(id), job_id BIGINT REFERENCES jobs(id),
    channel VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL,
    message_sid VARCHAR(100), translated_text TEXT, language VARCHAR(5),
    sent_at TIMESTAMP DEFAULT NOW(), delivered_at TIMESTAMP, read_at TIMESTAMP,
    UNIQUE(candidate_id, job_id)                    -- idempotency backstop (§9.4)
);
CREATE INDEX idx_notif_candidate ON notification_logs(candidate_id);
CREATE INDEX idx_notif_status ON notification_logs(status);

-- Transactional outbox for reliable PG -> ES sync (§9.2). NEW in v2.
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,            -- "job"
    aggregate_id BIGINT NOT NULL,                   -- job_id
    event_type VARCHAR(50) NOT NULL,                -- JOB_CREATED | JOB_UPDATED | JOB_CLOSED
    payload JSONB NOT NULL,                         -- the ES document
    version INT NOT NULL,                           -- aggregate version (stale-overwrite guard)
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSED | FAILED
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), processed_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_events (created_at) WHERE status = 'PENDING';
```

### 8.3 Key Queries

```sql
-- 1. Hyperlocal job search (PostGIS — also the ES-down fallback, §9.6): jobs within 5km
SELECT j.*, ST_Distance(j.location, ST_MakePoint(75.85, 22.72)::geography)/1000 AS distance_km
FROM jobs j
WHERE j.status = 'ACTIVE'
  AND ST_DWithin(j.location, ST_MakePoint(75.85, 22.72)::geography, 5000)
ORDER BY distance_km ASC LIMIT 20;

-- 2. Vector similarity: best job matches for a candidate
SELECT j.*, 1 - (cp.skill_embedding <=> j.requirement_vector) AS similarity_score,
       ST_Distance(j.location, cp.location)/1000 AS distance_km
FROM jobs j, candidate_profiles cp
WHERE cp.user_id = 42 AND j.status = 'ACTIVE'
  AND ST_DWithin(j.location, cp.location, cp.preferred_radius_km * 1000)
ORDER BY similarity_score DESC LIMIT 10;

-- 3. Find candidates for a new job (notification matching)
SELECT cp.user_id, cp.name, cp.phone, u.preferred_language,
       1 - (cp.skill_embedding <=> (SELECT requirement_vector FROM jobs WHERE id = 99)) AS similarity,
       ST_Distance(cp.location, (SELECT location FROM jobs WHERE id = 99))/1000 AS distance_km
FROM candidate_profiles cp
JOIN users u ON cp.user_id = u.id
JOIN notification_preferences np ON np.user_id = u.id
WHERE np.is_active = TRUE
  AND ST_DWithin(cp.location, (SELECT location FROM jobs WHERE id = 99), np.radius_km * 1000)
  AND (1 - (cp.skill_embedding <=> (SELECT requirement_vector FROM jobs WHERE id = 99))) > 0.6
ORDER BY similarity DESC LIMIT 100;
```

### 8.4 Elasticsearch Document Mapping

```json
{
  "mappings": { "properties": {
    "id": {"type":"long"}, "title": {"type":"text","boost":3}, "description": {"type":"text"},
    "category": {"type":"keyword"}, "skills": {"type":"text","boost":2},
    "city": {"type":"keyword"}, "state": {"type":"keyword"}, "pincode": {"type":"keyword"},
    "salary_min": {"type":"integer"}, "salary_max": {"type":"integer"},
    "status": {"type":"keyword"}, "company_name": {"type":"text"},
    "location": {"type":"geo_point"}, "created_at": {"type":"date"}, "slug": {"type":"keyword"}
  }},
  "settings": { "number_of_shards": 1, "number_of_replicas": 0,
    "analysis": { "analyzer": { "hindi_analyzer": {
      "type":"custom","tokenizer":"standard","filter":["lowercase","hindi_stemmer"] }},
      "filter": { "hindi_stemmer": {"type":"stemmer","language":"hindi"} } } }
}
```

**Why Elasticsearch + PostGIS (dual geo engines)?** PostgreSQL/PostGIS is the source of truth (ACID writes, complex spatial ops); Elasticsearch is the fast search read-side. They stay consistent via the outbox pattern (§9), not dual writes — and if ES is down, PostGIS is a built-in fallback (§9.6).

---

## 9. Availability & Consistency Patterns

The strongest section for NaukriNearby — the PostgreSQL↔Elasticsearch dual-store is a textbook consistency problem.

### 9.1 CAP positioning — two planes

| Path | Regime | Why |
|---|---|---|
| **Job search** (Elasticsearch geo) | **AP** | A searcher prefers fast results that may lag by seconds over a blocked search. |
| **Job posting / application / profile** (Postgres) | **CP** | Must commit atomically; no ghost records. |

> **Interview gold:** *"Postgres is my source of truth and runs CP — posting a job is one ACID transaction. Elasticsearch is a derived search index and runs AP — searches stay fast and available even if the index lags by a few seconds. The whole design is recognizing that 'post a job' and 'search jobs' have opposite consistency needs."*

### 9.2 The PG→ES sync problem (transactional outbox)

Dual writes are a trap: if the ES write fails after the PG commit, the job exists but is unsearchable — silent divergence. The fix is the **transactional outbox pattern**:

```
1. In ONE Postgres transaction: write the job row AND an outbox event row (atomic).
2. A sync worker polls the outbox (or tails the WAL via Debezium/CDC) and indexes into ES.
3. On success, mark the event PROCESSED. On failure, retry (it's still there).
```

Guarantees eventual consistency with no lost updates. **At-least-once indexing + idempotent ES upsert on `job_id` = effectively-once.** (Full starter code in the companion file.)

### 9.3 At-least-once + idempotent consumer = effectively-once
Both the PG→ES sync and the WhatsApp pipeline use Redis Streams (at-least-once). Idempotent effects (ES upsert keyed on `job_id`; WhatsApp dedup on `(user_id, job_id)`) make the result effectively-once.

### 9.4 Idempotent WhatsApp notifications (CRITICAL)
Notification jobs are at-least-once → without dedup you'd **text the same alert twice**. Dedup on `(user_id, job_id)` via Redis SETNX (7-day TTL) + a DB `UNIQUE(candidate_id, job_id)` backstop. (Implemented in §7.6.)

### 9.5 Read-your-writes for the job poster
A recruiter who just posted must see their job immediately, even before ES indexes it → the "my jobs" view reads from **Postgres** (source of truth); only public search reads from ES. Eventual consistency stays invisible to the poster.

### 9.6 Graceful degradation — PostGIS fallback (free availability win)
If Elasticsearch is down, search falls back to a PostGIS `ST_DWithin` query (§8.3, Query 1) in the source-of-truth Postgres. Slower, no fuzzy text, but the core "jobs near me" stays up.

> **Interview gold:** *"Because PostGIS is already in my source-of-truth Postgres, an Elasticsearch outage degrades to a PostGIS radius query instead of taking search down. My dual-store gives me a free availability backstop."*

### 9.7 Concurrent updates
Job edits use optimistic concurrency (the `version` column); the outbox event carries the version so ES never indexes a stale update over a newer one.

### 9.8 Availability tactics
- **Postgres:** primary + read replica; automated failover (Patroni / managed RDS Multi-AZ).
- **Elasticsearch:** 3-node cluster + replica shards at scale (single node acceptable for MVP — conscious trade-off).
- **Workers split by type** so resume-parse load can't starve WhatsApp delivery.

---

## 10. Caching & Messaging — Redis vs Kafka vs RabbitMQ

### 10.1 Comparison for THIS Project

| Criteria | Redis 7 (Cache + Streams + Pub/Sub) | Kafka | RabbitMQ | ZooKeeper |
|---|---|---|---|---|
| **Primary Purpose** | Multi-tool: cache + queue + rate limiter | Distributed event log | Message broker | Coordination (NOT a queue) |
| **Cache / OTP / rate limit** | Native | No | No | No |
| **Queue** | Streams (consumer groups, ack) | Overkill (3+ brokers) | Good, separate service | N/A |
| **Notification volume** | ~1000/day → trivial for Streams | Built for millions/sec | ~50K/sec | N/A |
| **Operational Complexity** | Very low (1 binary) | High (brokers + KRaft, 4GB+) | Medium (Erlang) | Used BY Kafka |

### VERDICT: Redis 7 — Single Tool for 5 Purposes

1. **OTP storage:** `otp:{phone}` (TTL 300s); `otp:attempts:{phone}` (max 5/hr).
2. **Session & data cache:** `session:{userId}`, `search:{queryHash}` (5min), `job:{jobId}` (15min), `profile:{userId}` (1h).
3. **Rate limiting:** API (100/min), WhatsApp (3/day), LLM TPM, OTP (5/hr).
4. **Notification queue (Redis Streams):** `notification-jobs` with consumer groups + ack + XPENDING.
5. **Resume-parse queue (Redis Streams):** `resume-parse-jobs`; frontend polls parse-status.
6. **(v2) Idempotency + outbox-sync coordination:** `notif:sent:{user}:{job}`, `webhook:seen:*`.

**Why NOT Kafka?** ~1000 notifications/day doesn't justify 3 brokers + KRaft + 4GB RAM. Redis Streams gives consumer groups, ack, and pending visibility in the Redis we already run. Migrate to Kafka at 100K+/day with multiple independent consumers. **Why NOT RabbitMQ?** Redis already covers 5 needs; adding RabbitMQ means a separate Erlang service for one. **Why NOT ZooKeeper?** It's a coordination service, not a queue (used by old Kafka pre-KRaft).

---

## 11. Design Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Strategy** | Search ranking (proximity-first / skill-first / recency-first) | Different surfaces need different ranking; swappable without touching SearchService |
| **Observer** | Job events → ES indexer, notification trigger, analytics | Decouples job creation from downstream effects |
| **Builder** | Elasticsearch query construction | Optional filters without telescoping constructors |
| **Template Method** | Notification pipeline (WhatsApp/SMS share skeleton) | Common compose/translate/rate-limit; channel-specific `deliver()` |
| **Repository** | Spring Data JPA + custom PostGIS/pgvector queries | Business logic free of SQL/JPA |
| **Adapter** | LLM provider, embedding, translation | Swap models/providers via config (§2) |
| **Circuit Breaker** | LLM, Twilio, Bhashini, ES | Fail fast on outage → fallback (§12) |
| **CQRS-lite** | Writes → Postgres (truth), reads → Elasticsearch | Integrity on writes, fast geo+text on reads |
| **Transactional Outbox** | PG→ES sync | Reliable dual-store consistency (§9) |
| **Event-Driven Architecture** | Overall | job created → ES + notify + analytics; resume uploaded → parse + embed |

Sample (Strategy):
```java
public interface SearchRankingStrategy { float calculateScore(JobDocument j, JobSearchRequest r); }
class ProximityFirstStrategy implements SearchRankingStrategy { /* 0.5 geo + 0.3 skill + 0.2 recency */ }
class SkillMatchFirstStrategy implements SearchRankingStrategy { /* 0.2 geo + 0.5 skill + 0.3 recency */ }
class RecencyFirstStrategy implements SearchRankingStrategy { /* 0.3 geo + 0.3 skill + 0.4 recency */ }
```

---

## 12. Resilience Patterns (Consolidated)

| Pattern | Where | Implementation |
|---|---|---|
| **Circuit breaker** | LLM, Bhashini, Twilio, ES | Resilience4j; open at 50% failure / 20 calls; fallbacks per service |
| **Retry + backoff + jitter** | LLM 429s, Twilio, ES blips | Max 3; respect `Retry-After`; jitter |
| **Timeout** | Every external call | LLM 60s, Twilio 10s, ES 3s |
| **Bulkhead** | parse / sync / notify worker pools | Separate pools so resume-parse load can't starve WhatsApp delivery |
| **Rate limiter** | LLM + Twilio + Bhashini | Redis sliding window; respect Twilio WhatsApp tier limits |
| **Dead-letter queue** | Failed parse / sync / notify jobs | After N retries → DLQ stream + alert; flagged for manual review |
| **Graceful degradation** | ES outage → **PostGIS fallback** (§9.6); translation outage → send English | Site stays usable |
| **Idempotency** | WhatsApp sends + ES indexing | dedup `(user_id, job_id)`; ES upsert on `job_id` (§9) |
| **Backpressure** | All queues | Streams `BLOCK` + bounded concurrency |
| **Health/readiness probes** | K8s | liveness + readiness; readiness false while ES sync warming |
| **Outbox + retry** | PG→ES sync | Durable events; survives ES downtime and drains on recovery |

> **Interview gold:** *"My favorite resilience detail: if Elasticsearch dies, search falls back to a PostGIS radius query in my source-of-truth Postgres. I lose fuzzy text and it's slower, but 'jobs near me' stays up. The dual-store gives me a free degradation path. And failed jobs go to a dead-letter stream after N retries so they're never silently lost."*

---

## 13. Mitigation Strategies

| Risk | Mitigation |
|---|---|
| **PG↔ES divergence** | Outbox pattern, not dual writes; idempotent ES upsert on `job_id`; nightly reconciliation |
| **Double-texting users** | Idempotent WhatsApp dedup `(user_id, job_id)` + DB UNIQUE backstop (§9.4) |
| **Resume PII leakage** | Encrypt resumes at rest; India-resident storage (DPDP Act); redact phone/email in logs; signed-URL file access |
| **Bad resume extraction** | Eval harness (§18) measures skill F1; low-confidence parses flagged for review |
| **Bad regional translation** | Translation eval set (§18); fall back to English on low confidence; reviewed templates |
| **LLM cost runaway** | Mid-tier extraction model; self-hosted embeddings; cache parses by file hash; daily token budget |
| **Twilio cost & spam** | Rate-limit alerts (3/day/user); opt-in/opt-out; dedup |
| **Fake/scam job postings** | Recruiter verification (OTP + GST/phone); fraud-signal classifier (§19); report button |
| **Geo spoofing / bad coords** | Validate lat/lng; clamp radius; reject coords outside India bounding box |
| **ES heavy/injection queries** | Parameterized queries; cap radius + result size; query timeout |
| **Flaky Tier-2/3 networks** | Offline-first PWA (§19); idempotent retries; small payloads |
| **DB connection exhaustion** | HikariCP bounded pool; PgBouncer; separate replica pool for search fallback |
| **Demo fails at interview** | Pre-recorded video; seeded jobs + a demo resume that always parses |

---

## 14. Docker & Kubernetes

> **Backend runtime:** the image builds a **Spring Boot 4.0.6** (Spring Framework 7) application with **Gradle 9** on the Java 21 LTS Temurin base shown below. Spring Boot 4.0.6 supports Java 17–26, so the `eclipse-temurin:21` images are correct and unchanged — only the framework generation moved from 3.x to 4.0.6.

### 14.1 Dockerfiles

**Backend (multi-stage):**
```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon
COPY src/ src/
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java","-XX:+UseZGC","-XX:MaxRAMPercentage=75.0","-jar","app.jar"]
```

**Frontend (multi-stage):**
```dockerfile
# Next.js 16 requires Node 20.9+, but Node 20 is EOL (Apr 2026) — use Node 24 LTS.
# Turbopack is the default bundler in Next.js 16; `next build` needs no --turbopack flag.
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run build

FROM node:24-alpine
WORKDIR /app
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
EXPOSE 3000
ENV NODE_ENV=production
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:3000/api/health || exit 1
CMD ["node","server.js"]
```

### 14.2 Docker Compose (local + production-ready)

```yaml
version: '3.8'
services:
  frontend:
    build: ./frontend
    ports: ["3000:3000"]
    depends_on: [backend]
    environment:
      - NEXT_PUBLIC_API_URL=http://localhost:8080
      - NEXT_PUBLIC_MAP_TILE_URL=https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png
  backend:
    build: ./backend
    ports: ["8080:8080"]
    depends_on:
      postgres: {condition: service_healthy}
      redis: {condition: service_healthy}
      elasticsearch: {condition: service_healthy}
      minio: {condition: service_healthy}
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/naukrinearby
      - SPRING_DATASOURCE_USERNAME=postgres
      - SPRING_DATASOURCE_PASSWORD=postgres
      - SPRING_REDIS_HOST=redis
      - SPRING_ELASTICSEARCH_URIS=http://elasticsearch:9200
      - EXTRACTION_PROVIDER=${EXTRACTION_PROVIDER:-gpt5mini}
      - LLM_API_KEY=${LLM_API_KEY}
      - TWILIO_ACCOUNT_SID=${TWILIO_ACCOUNT_SID}
      - TWILIO_AUTH_TOKEN=${TWILIO_AUTH_TOKEN}
      - TWILIO_WHATSAPP_FROM=${TWILIO_WHATSAPP_FROM}
      - TRANSLATION_PROVIDER=${TRANSLATION_PROVIDER:-bhashini}
      - BHASHINI_API_KEY=${BHASHINI_API_KEY}
      - MINIO_ENDPOINT=http://minio:9000
      - MINIO_ACCESS_KEY=minioadmin
      - MINIO_SECRET_KEY=minioadmin
    healthcheck:
      test: ["CMD","curl","-f","http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
  postgres:
    image: postgis/postgis:16-3.4-alpine
    ports: ["5432:5432"]
    environment: [POSTGRES_DB=naukrinearby, POSTGRES_USER=postgres, POSTGRES_PASSWORD=postgres]
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./backend/src/main/resources/init.sql:/docker-entrypoint-initdb.d/01-init.sql
    healthcheck:
      test: ["CMD-SHELL","pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
    command: redis-server --maxmemory 256mb --maxmemory-policy allkeys-lru
    volumes: [redis_data:/data]
    healthcheck:
      test: ["CMD","redis-cli","ping"]
      interval: 10s
      timeout: 5s
      retries: 5
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.13.0
    ports: ["9200:9200"]
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - "ES_JAVA_OPTS=-Xms512m -Xmx512m"
    volumes: [es_data:/usr/share/elasticsearch/data]
    healthcheck:
      test: ["CMD-SHELL","curl -f http://localhost:9200/_cluster/health || exit 1"]
      interval: 15s
      timeout: 10s
      retries: 5
  minio:
    image: minio/minio:latest
    ports: ["9000:9000","9001:9001"]
    environment: [MINIO_ROOT_USER=minioadmin, MINIO_ROOT_PASSWORD=minioadmin]
    volumes: [minio_data:/data]
    command: server /data --console-address ":9001"
    healthcheck:
      test: ["CMD","mc","ready","local"]
      interval: 10s
      timeout: 5s
      retries: 5
  prometheus:
    image: prom/prometheus:latest
    ports: ["9090:9090"]
    volumes: ["./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml"]
  grafana:
    image: grafana/grafana:latest
    ports: ["3001:3000"]
    environment: [GF_SECURITY_ADMIN_PASSWORD=admin]
    volumes:
      - grafana_data:/var/lib/grafana
      - ./monitoring/grafana/dashboards:/etc/grafana/provisioning/dashboards
      - ./monitoring/grafana/datasources:/etc/grafana/provisioning/datasources
  langfuse:
    image: langfuse/langfuse:latest
    ports: ["3002:3000"]
    depends_on: [postgres]
volumes: {postgres_data: , redis_data: , es_data: , minio_data: , grafana_data: }
```

### 14.3 Kubernetes (reference)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata: {name: naukrinearby-backend, labels: {app: naukrinearby-backend}}
spec:
  replicas: 2
  selector: {matchLabels: {app: naukrinearby-backend}}
  template:
    metadata:
      labels: {app: naukrinearby-backend}
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: "/actuator/prometheus"
        prometheus.io/port: "8080"
    spec:
      containers:
        - name: backend
          image: yourdockerhub/naukrinearby-backend:latest
          ports: [{containerPort: 8080}]
          envFrom:
            - secretRef: {name: naukrinearby-secrets}
            - configMapRef: {name: naukrinearby-config}
          resources:
            requests: {memory: "512Mi", cpu: "250m"}
            limits: {memory: "1Gi", cpu: "1000m"}
          readinessProbe:
            httpGet: {path: /actuator/health/readiness, port: 8080}
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet: {path: /actuator/health/liveness, port: 8080}
            initialDelaySeconds: 60
            periodSeconds: 30
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata: {name: naukrinearby-backend-hpa}
spec:
  scaleTargetRef: {apiVersion: apps/v1, kind: Deployment, name: naukrinearby-backend}
  minReplicas: 2
  maxReplicas: 6
  metrics:
    - type: Resource
      resource: {name: cpu, target: {type: Utilization, averageUtilization: 70}}
```

Separate the **notification + sync workers** into their own Deployment that auto-scales on Redis Stream pending count, so a job-posting spike doesn't starve the API (see §17 scaling decision).

---

## 15. Deployment Strategies — Rolling / Blue-Green / Canary

| Strategy | How | When here |
|---|---|---|
| **Rolling update** (default K8s) | `maxSurge=1, maxUnavailable=0` | Demo default — zero downtime |
| **Blue-green** | v2 fleet beside v1; flip LB | Risky changes (ES mapping, schema) — instant rollback |
| **Canary** | 5%→25%→100%, watch error rate + **extraction quality** | Prompt/model/embedding changes — canary on quality, not just HTTP errors |

**Elasticsearch reindex (zero-downtime):** never mutate a live mapping. Create `jobs_v2` with the new mapping, reindex from `jobs_v1`, then atomically flip the **index alias** `jobs → jobs_v2`. Searches never see downtime. *Say "alias flip" — it signals a real ES migration.*

**DB migrations:** Flyway/Liquibase **expand-contract** so rolling updates stay safe with schema changes.

> **Interview gold:** *"For an Elasticsearch mapping change I reindex into a new index and flip an alias atomically — searches never go down. For a prompt or embedding change I canary on extraction quality via my eval signal, not just HTTP 5xx, because a model can get faster but less accurate."*

---

## 16. CI/CD Pipeline — GitHub Actions

> **Toolchain:** CI builds the backend with **Gradle 9** and **Java 21** against **Spring Boot 4.0.6** (Spring Framework 7). The `setup-java` step stays on Java 21 — fully supported on 4.0.6 — so no workflow change is needed beyond the dependency/version bump in `build.gradle.kts`.

```yaml
# .github/workflows/deploy.yml
name: CI/CD Pipeline
on:
  push: {branches: [main]}
  pull_request: {branches: [main]}
env:
  BACKEND_IMAGE: ${{ secrets.DOCKER_USERNAME }}/naukrinearby-backend
  FRONTEND_IMAGE: ${{ secrets.DOCKER_USERNAME }}/naukrinearby-frontend
jobs:
  test-backend:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgis/postgis:16-3.4-alpine
        env: {POSTGRES_DB: naukrinearby_test, POSTGRES_USER: postgres, POSTGRES_PASSWORD: postgres}
        ports: [5432:5432]
        options: --health-cmd pg_isready --health-interval 10s
      redis:
        image: redis:7-alpine
        ports: [6379:6379]
        options: --health-cmd "redis-cli ping" --health-interval 10s
      elasticsearch:
        image: docker.elastic.co/elasticsearch/elasticsearch:8.13.0
        env: {discovery.type: single-node, xpack.security.enabled: false, ES_JAVA_OPTS: "-Xms256m -Xmx256m"}
        ports: [9200:9200]
        options: --health-cmd "curl -f http://localhost:9200" --health-interval 15s
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: {java-version: '21', distribution: 'temurin'}
      - run: cd backend && ./gradlew test
        env:
          SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/naukrinearby_test
          SPRING_ELASTICSEARCH_URIS: http://localhost:9200
          SPRING_REDIS_HOST: localhost

  test-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with: {node-version: '20'}
      - run: cd frontend && npm ci && npm run lint && npm run test

  # NEW IN v2 — AI quality gate (§18)
  resume-extraction-eval:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-python@v5
        with: {python-version: '3.12'}
      - run: cd eval && pip install -r requirements.txt && python run_eval.py --ci
        env: {NAUKRI_API: ${{ secrets.STAGING_API_URL }}, EXTRACTION_API_KEY: ${{ secrets.LLM_API_KEY }}}
        # FAILS the build if skill-extraction F1 drops > 5 points vs baseline

  build-and-push:
    needs: [test-backend, test-frontend, resume-extraction-eval]
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: docker/login-action@v3
        with: {username: ${{ secrets.DOCKER_USERNAME }}, password: ${{ secrets.DOCKER_PASSWORD }}}
      - run: |
          docker build -t $BACKEND_IMAGE:${{ github.sha }} -t $BACKEND_IMAGE:latest ./backend
          docker push $BACKEND_IMAGE --all-tags
          docker build -t $FRONTEND_IMAGE:${{ github.sha }} -t $FRONTEND_IMAGE:latest ./frontend
          docker push $FRONTEND_IMAGE --all-tags

  deploy:
    needs: build-and-push
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.EC2_HOST }}
          username: ubuntu
          key: ${{ secrets.EC2_SSH_KEY }}
          script: |
            cd /opt/naukrinearby
            docker compose pull && docker compose up -d --remove-orphans && docker system prune -f
```

---

## 17. Monitoring & Observability

### Prometheus
```yaml
global: {scrape_interval: 15s}
scrape_configs:
  - job_name: 'naukrinearby-backend'
    metrics_path: /actuator/prometheus
    static_configs: [{targets: ['backend:8080']}]
  - job_name: 'elasticsearch'
    static_configs: [{targets: ['elasticsearch:9200']}]
  - job_name: 'redis'
    static_configs: [{targets: ['redis-exporter:9121']}]
```

### Grafana — 4 panels
1. **Search latency** (`search_duration_seconds_bucket`) — P50 < 100ms, P95 < 500ms.
2. **Resume parse time** (`resume_parse_duration_seconds_bucket`) — P50 < 10s, P95 < 30s.
3. **Notification delivery rate** (`notifications_sent_total{status="DELIVERED"}` / total) — > 95%.
4. **Notification queue depth** (`redis_stream_pending_count{stream="notification-jobs"}`) — alert > 100 for 5min.

Plus **Langfuse** (§18) for per-parse cost, tokens, latency, JSON-validity.

### The "Scaling Decision" to Document
> **Separate notification + sync workers from the API backend.** Initially notification sending ran inside the API; when 50 jobs were posted in an hour, matching + WhatsApp sending consumed threads and API P95 spiked from 100ms to 800ms. **Option A:** scale all backend replicas (wasteful). **Option B:** extract notification + outbox-sync processing into a dedicated consumer (same codebase, different `@Profile`) that auto-scales on Redis Stream pending count. **Decision:** Option B — API P95 dropped back to 150ms, notification throughput up 3×. **Revisit** at multi-channel scale (SMS + email + push) with Kafka topics per channel.

---

## 18. DevOps / MLOps Wrapper

Three AI components can silently degrade — each needs an eval.

### 18.1 Resume-extraction eval (headline)
A labeled set of ~30 resumes (Hindi/English-mixed, photo resumes, informal titles like "dukaan pe kaam") with expected fields. Metrics: **skill-extraction F1**, **field accuracy** (city/experience), **JSON-validity rate**. Runs in CI; fails if skill-F1 drops >5 points. (Starter code in the companion file.)

> **Interview gold:** *"I measure my parser — skill-extraction F1 on a benchmark of real Bharat resumes including Hindi/English-mixed and informal titles. It runs in CI against a committed baseline, so a prompt change that quietly got worse can't merge."*

### 18.2 Match-quality eval (vector search)
A labeled set of (candidate → relevant job IDs); measure **recall@10** and **MRR**. Compare embedding models (bge-m3 vs alternatives) on this set — "I chose my embedding model with data."

### 18.3 Translation-quality eval (Bharat-critical)
A reviewed set of (English alert → expected Hindi/Tamil/etc.); native-speaker spot-check or a translation-quality metric; fall back to English on low confidence. A garbled alert in someone's mother tongue is a product-safety issue.

### 18.4 LLM observability (Langfuse)
Trace every parse: model, prompt version, tokens, cost, latency, validity. Panels: cost per parse, p95 parse latency, JSON-validity rate, parses per model.

### 18.5 Prompt versioning + CI gate
Extraction prompt as a versioned file (`prompts/resume_parse_v3.txt`); each parse logs its version; new versions must beat old on the eval. CI fails on F1 regression >5pts or cost regression >20%.

### 18.6 Feedback flywheel
When a candidate corrects their auto-parsed profile, that correction is gold-labeled data → add to the eval set; recurring misses → tune the prompt.

---

## 19. Bharat Differentiator Features

Build 1–2; write the rest into `docs/future-work/`.

### 🟢 G1 — WhatsApp-First Application Flow (headline — BUILD THIS)
Let a candidate **apply to a job entirely via WhatsApp conversation** — no app install, no resume upload. The bot collects name/skills/experience in their language; the resume pipeline structures the chat into a profile. Uses Twilio's inbound WhatsApp webhooks.

> *"A daily-wage worker in Kanpur with a ₹5,000 phone doesn't install apps — but they're on WhatsApp all day. So they apply by chatting in Hindi, and my pipeline structures the conversation into a candidate profile. That's building for the next billion, not porting a city product to a village."*

### 🟢 G2 — Offline-First PWA for Flaky Networks (BUILD THIS — on-theme)
Service worker caches the job feed, queues applications offline, syncs on reconnect. Lighthouse PWA score in the README. This *is* the Bharat product-thinking signal.

### 🟢 G3 — Resume-Extraction Eval Harness (BUILD THIS)
§18.1 — both MLOps practice and differentiator; almost no fresher measures AI quality.

### 🟡 G4 — Hybrid Search (BM25 + Vector, Reciprocal Rank Fusion)
Combine Elasticsearch BM25 with pgvector semantic match via RRF — catches both exact-keyword and semantically-similar jobs. Measurable via §18.2.

### 🟡 G5 — Transliteration + Voice Search
Type Hindi in Latin script ("naukri chahiye") or **speak** the search (Web Speech API → Bhashini ASR). Low-literacy users are a huge Bharat segment competitors ignore.

### 🟠 G6 — Fake-Job / Fraud Detection (write up / light build)
Light classifier on posting text + recruiter signals (unverified phone, payment-request keywords) flags scam postings. Real product-safety thinking.

### 🟠 G7 — Skill-Gap Recommendations (write up)
"You're missing *Tally* for this accounting job — here's a free course." Uses the same skill embeddings.

### 🟠 G8 — Phone-OTP-Only Auth + DPDP Compliance (write up)
Phone-first OTP login; India-resident data storage for DPDP Act compliance.

| If you have… | Build |
|---|---|
| 1 extra week | G3 (eval) + G2 (offline PWA) |
| 2 extra weeks | + G1 (WhatsApp-first apply) — the headline |
| 3 extra weeks | + G4 (hybrid search) |
| Writing only | G5–G8 as `docs/future-work/` + ADRs |

---

## 20. README Blueprint

```markdown
# NaukriNearby — Hyperlocal Job Board for Bharat 🇮🇳
> Connecting Tier-2/3 city workers to nearby jobs.
> AI-parsed resumes. WhatsApp alerts in your language.

[LIVE DEMO](https://naukrinearby.in) | [Video Demo](https://youtube.com/...)
![Architecture](docs/architecture.png)
![Job Search with Map](docs/screenshot-search.png)
![AI Resume Parsing](docs/screenshot-resume.png)
![WhatsApp Alert](docs/screenshot-whatsapp.png)

## Why This Exists
500M+ Indians in Tier-2/3 cities find jobs by word-of-mouth. NaukriNearby brings
hyperlocal job discovery to their phones with WhatsApp-first alerts in regional languages.

## Features
- Geotagged postings + map search (Elasticsearch geo_distance, PostGIS fallback)
- AI resume parser (LLM adapter) for Hindi+English resumes — quality measured by eval
- Semantic job matching (pgvector) · WhatsApp alerts in regional languages (Bhashini + Twilio)
- Reliable PG→ES sync (transactional outbox) · idempotent notifications
- SEO job pages (Next.js SSR + JSON-LD) · phone+OTP auth

## Tech Stack
Next.js 16 (React 19.2) · Spring Boot 4.0.6 (Java 21) · PostgreSQL 16 + PostGIS + pgvector ·
Elasticsearch 8 · Redis 7 · LLM adapter (GPT-5-mini-class) · bge-m3 embeddings ·
Bhashini · Twilio WhatsApp · Langfuse · Docker · GitHub Actions · Prometheus + Grafana

## Architecture Decisions
1. PostgreSQL + PostGIS + pgvector over MongoDB — one DB for relational + geo + vector
2. Transactional outbox for PG→ES sync — no dual-write divergence
3. Redis Streams over Kafka — operational simplicity at our notification volume
4. Model-agnostic LLM adapter + Bhashini for translation — right tool per AI job
5. Separate notification/sync workers — keeps API latency low during spikes
6. Eval-gated CI — merges blocked on resume-extraction regression
7. Leaflet + OSM over Google Maps — free, India coverage, smaller bundle

## Quick Start
    git clone https://github.com/you/naukrinearby.git && cd naukrinearby
    cp .env.example .env    # add LLM + Twilio + Bhashini keys
    docker compose up -d
    # Frontend :3000 · Backend :8080 · ES :9200 · MinIO :9001 · Grafana :3001 · Langfuse :3002

## License
MIT
```

---

## 21. Interview Prep — Questions & Answers

### System Design
**Q1: Walk me through the architecture.** Three-tier: Next.js SSR frontend (job pages must be Google-indexed), Spring Boot backend, data layer of Postgres (PostGIS + pgvector) + Elasticsearch. A posted job writes to Postgres (source of truth) plus an outbox event in one transaction; a sync worker drains the outbox into ES. Matching runs pgvector cosine similarity + PostGIS distance. Matches get WhatsApp alerts translated via Bhashini, sent via Twilio, through an idempotent Redis Streams pipeline.

**Q2: Why both Elasticsearch and PostGIS?** CQRS-lite. Postgres/PostGIS is the write-side source of truth (ACID, complex spatial ops). Elasticsearch is the read-side search (sub-100ms geo + text + scoring). They sync via the outbox pattern; if ES is down, search degrades to a PostGIS radius query.

**Q3: Is your system CP or AP?** Both. Postgres (truth) is CP — posting is one ACID transaction. Elasticsearch (index) is AP — searches stay fast/available even if the index lags. Opposite consistency needs for "post" vs "search."

**Q4: How do Postgres and Elasticsearch stay consistent?** Transactional outbox, not dual writes. Job + outbox event commit in one transaction; a worker drains to ES with retries and an idempotent upsert on `job_id`. At-least-once indexing + idempotent upsert = effectively-once. Dual writes would risk silent divergence.

**Q5: What if Elasticsearch goes down?** Search falls back to a PostGIS `ST_DWithin` query in source-of-truth Postgres — slower, no fuzzy text, but "jobs near me" stays up. Free fallback from the dual-store.

**Q6: A user gets the same job alert twice — prevent it?** Idempotent send: dedup on `(user_id, job_id)` via Redis SETNX (7-day TTL) + a DB UNIQUE backstop. At-least-once delivery + idempotent send = effectively-once.

**Q7: Why PostgreSQL over MongoDB?** Relational data (employers→jobs, candidates→profiles, applications); PostGIS gold-standard geospatial; pgvector for embeddings — one DB does relational + geo + vector. MongoDB would need 2–3 stores.

**Q8: Scale to 10×?** CDN-cache SSR pages; horizontal API replicas; notification/sync workers already separate → scale independently; Postgres read replicas; ES second data node + replica shards; Redis still fine. Real bottleneck becomes LLM rate limits → batching + cheaper model for triage.

**Q9: Why Redis over Kafka?** ~1000 notifications/day doesn't justify 3 brokers + KRaft + 4GB RAM. Redis Streams gives consumer groups + ack + pending visibility in the Redis we already run. Migrate to Kafka at 100K+/day with multiple independent consumers.

### AI / MLOps
**Q10: Why did you move off GPT-4, and why a mid-tier model?** GPT-4 is dated; and resume extraction is high-volume structured extraction, which a mid-tier model does accurately at ~10× lower cost. Each AI job (extraction, vision, embedding, translation) is behind an adapter using the right model.

**Q11: Why Bhashini over an LLM for translation?** It's India's government translation stack for 22 languages — more accurate on low-resource languages, cheaper, the credible Bharat choice. A garbled alert in someone's mother tongue is a product-safety issue I measure with an eval set.

**Q12: How do you know the resume parser is accurate?** A 30-resume benchmark (Hindi/English-mixed, photo resumes, informal titles) with expected fields; I measure skill-extraction F1 and field accuracy, gated in CI.

**Q13: How does AI resume parsing work?** Image → vision model OCR (handles photographed paper resumes); text → LLM adapter with a strict JSON schema (handles Hindi-English code-mixing, maps informal titles); skills → bge-m3 embedding → pgvector for semantic matching ("delivery executive" ≈ "delivery boy" ≈ "डिलीवरी").

### Frontend / DevOps
**Q14: Why Next.js over React SPA?** SEO — job pages must be server-rendered with meta + JSON-LD so Google indexes "delivery boy Indore." Server components also cut JS shipped to budget phones on 3G.

**Q15: How does map search work?** Leaflet + OSM (42KB, free, great India coverage); map and list synced; panning fires a geo_bounding_box query; markers clustered; RadiusSlider updates the geo_distance param.

**Q16: What's in your Docker Compose?** Frontend, backend, Postgres (PostGIS), Redis, Elasticsearch, MinIO, Prometheus + Grafana, Langfuse — health-checked, dependency-ordered. ~3GB, fits a t3.medium.

**Q17: A scaling decision you made?** Separated notification + sync workers from the API; API P95 dropped from 800ms back to 150ms during posting spikes (see §17).

**Q18: GPT/LLM is down — what happens?** Circuit breaker; resume upload still stored in MinIO; retry from the Redis Stream on recovery; candidate can fill fields manually meanwhile.

**Q19: Zero-downtime Elasticsearch mapping change?** Reindex into `jobs_v2`, flip the `jobs` alias atomically. For prompt/model changes, canary on extraction quality.

**Q20: At Meesho/Flipkart scale?** Citus/Aurora sharding by city; Kafka for the notification pipeline; ES cluster with city-based routing; CDN for the frontend; S3 + CloudFront for resume files.

---

## 22. Deployment Checklist — Go Live

- [ ] `docker compose up` runs all services cleanly
- [ ] Phone + OTP login works end-to-end
- [ ] Employer posts a geotagged job (map pin); **job + outbox event commit atomically**
- [ ] Job appears in Elasticsearch within 2s **via the outbox sync worker** (not dual write)
- [ ] Candidate uploads resume → LLM parses → profile auto-fills
- [ ] Geo search "within 5km" returns correct results; **ES-down → PostGIS fallback works**
- [ ] WhatsApp alert arrives in the candidate's language (Bhashini); **re-delivery is deduped**
- [ ] Job detail page is SSR (view source shows content); JSON-LD passes Rich Results Test
- [ ] CI/CD green incl. **resume-extraction eval gate**
- [ ] Deployed publicly with HTTPS
- [ ] Grafana (4 panels) + Langfuse dashboards accessible (screenshots for README)
- [ ] README: architecture diagram, screenshots, setup, live link, scaling decision
- [ ] `docs/`: SCALING.md, ADRs (outbox, model-adapter, Bhashini), eval results
- [ ] 2-minute demo video (include map search, WhatsApp alert, a deduped re-delivery)
- [ ] Tested on mobile (Tier-2/3 users are mobile-first)

### Cost Estimate (Monthly, live demo)
| Service | Provider | Cost |
|---|---|---|
| EC2 t3.medium (2 vCPU, 4GB) | AWS | ~$30/mo (~₹2,500) |
| OR Railway.app | Railway | ~$5–20/mo |
| LLM API (mid-tier extraction) | provider | ~$3–8/mo (demo) |
| Twilio WhatsApp | Twilio | ~$5/mo sandbox |
| Bhashini | Govt of India | Free/low-cost tier |
| Domain (naukrinearby.in) | registrar | ~₹500/yr |
| SSL | Cloudflare | Free |
| **Total** | | **₹2,000–5,000/month** |

---

## 23. Final Word — Why This Project Wins Interviews

**For "Building for Bharat" companies (CRED, PhonePe, Meesho, OkCredit):** Tier-2/3-first — WhatsApp-first apply, regional languages via Bhashini, phone+OTP, offline PWA, real network/phone constraints handled.

**For engineering-focused companies (Razorpay, Atlassian, Thoughtworks):** geospatial dual-engine with CQRS justification; **transactional outbox for reliable PG↔ES consistency**; AI integration *measured* with an eval harness; design patterns; full DevOps **+ MLOps**; documented scaling decision.

**For scale-focused companies (Flipkart, Swiggy, Dunzo):** articulate sharding, Kafka migration, ES clustering, alias-flip reindexing; pragmatic tradeoffs with reasoning; measured latency/delivery/queue depth.

**The differentiator:** Most freshers build a todo app. You built a geospatially-aware, AI-powered, multilingual, WhatsApp-integrated platform — with a real product thesis, reliable dual-store consistency, idempotent notifications, and measured AI quality. When the interviewer asks "Why did you build this?", your answer is: *"Because 500 million Indians in Tier-2/3 cities find jobs by word-of-mouth, and I wanted to solve that — correctly, for how Bharat actually uses phones."*

Build it. Deploy it. Put the live link on your resume.

---

*Merged master document (v2) — NaukriNearby Hyperlocal Job Board for India's Tier-2/3 Cities (2026)*
