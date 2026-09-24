# NaukriNearby

Hyperlocal job board for India. Candidates find nearby work by map and radius, upload a resume that is parsed into a profile, and apply in one tap. Employers post geotagged jobs. Matching candidates can be alerted over WhatsApp. Postgres is the source of truth; Elasticsearch is a derived search index kept in sync through a transactional outbox.

The repository has two apps:

| Path | What it is |
| --- | --- |
| [`naukrinearby/`](naukrinearby/) | Spring Boot 4 API, workers, Flyway migrations, eval harness, Compose, monitoring, Kubernetes manifests |
| [`frontend/`](frontend/) | Next.js 16 web app (search, job pages, login, resume upload, apply, save) |

## Features

- Phone + OTP login that issues stateless RS256 JWTs, with refresh tokens stored in Redis and a transparent 401 refresh on the client.
- Employer job posts with category, skills, salary, and a map location constrained to India.
- Public hyperlocal search: text + category + radius. Results are cached in Redis. If Elasticsearch is down, search falls back to PostGIS `ST_DWithin` and the API reports `fellBackToPostgis`.
- Personalized recommendations (pgvector similarity plus distance) and an optional hybrid BM25 + vector ranking path (reciprocal rank fusion).
- Resume upload to MinIO (PDF or photo), async parse, and a candidate profile filled from the extraction result.
- Apply is idempotent (`409` on a second apply). Candidates can save and unsave jobs.
- Notification preferences, a Redis Streams alert pipeline, and dedup so the same candidate is not messaged twice for the same job.
- WhatsApp status webhooks (Twilio signature check) and a WhatsApp-first apply conversation path.
- Right-to-erasure: `DELETE /api/candidate/account` removes the user graph and the stored resume object.
- Internal eval endpoints (off unless `EVAL_ENABLED=true`) that exercise the production parse, match, and translate paths.

External providers ship as stubs so the app runs with no API keys. Real LLM, embedding, translation, OTP, WhatsApp, vision, and geocoding adapters are selected by environment variables.

## Tech Stack

| Layer | Choice |
| --- | --- |
| Backend | Spring Boot 4.0.6, Spring Framework 7, Java 21, Gradle (Kotlin DSL) |
| Frontend | Next.js 16 App Router, React 19.2, TypeScript, Node.js 24, Turbopack |
| UI | Tailwind CSS v4, shadcn/ui, Leaflet / OpenStreetMap |
| Client data | Zod at the API boundary, TanStack Query for search and resume-parse polling |
| Source of truth | PostgreSQL 16 + PostGIS + pgvector |
| Search index | Elasticsearch 9.2 (Java client 9.2.8, bundled with Spring Boot 4) |
| Cache and queues | Redis 7 (cache, Streams, rate limits, refresh tokens, idempotency) |
| Object storage | MinIO (S3-compatible) for resumes |
| Auth | Phone OTP → JWT (RS256). OTP, WhatsApp, and translation default to stubs |
| AI | Config-selected adapters: GPT-5-mini-class extraction, local or remote embeddings (1024 dims), Bhashini translation |
| Resilience | Resilience4j retry and circuit breakers on external calls |
| Observability | Micrometer, Prometheus, Grafana, optional Langfuse |
| CI | GitHub Actions |

## Architecture

Writes that must appear in search commit the row and an `outbox_events` record in one Postgres transaction. `OutboxSyncWorker` drains that outbox into Elasticsearch with an idempotent, version-guarded upsert on `job_id`. The API never writes Postgres and Elasticsearch in the same request.

Reads prefer Elasticsearch (geo distance, Hindi analyzer, Redis cache). A failed search uses PostGIS in Postgres. Job detail for a poster is read from Postgres.

```text
Browser (Next.js :3000)
        │  REST + JWT
        ▼
Spring Boot API (:8080)          worker profile (outbox + notifications)
        │                                  │
        ├─ Postgres 16  ◄── outbox ────────┤
        │    PostGIS + pgvector            ▼
        ├─ Redis 7                    Elasticsearch 9.2
        └─ MinIO (resumes)
```

API pods can run with `SPRING_PROFILES_ACTIVE=api`, which turns the sync and notification workers off. A separate worker deployment runs them. Manifests live in [`naukrinearby/k8s/`](naukrinearby/k8s/).

Postgres is the consistent store for posts and applications. Elasticsearch is the available store for search and may lag by a short interval. Notifications dedupe on `(user, job)` with Redis `SETNX` and a unique `notification_logs` row.

## Quick Start via Docker

Docker Compose starts the datastores (and, on request, the frontend and the monitoring stack). The API runs on the host so Gradle and the debugger stay local.

Prerequisites: Docker, JDK 21, and (for the UI) Node.js 24.

```bash
cd naukrinearby
docker compose up -d
cp .env.example .env
./gradlew bootRun
```

On Windows use `.\gradlew.bat bootRun`.

Defaults match Compose: Postgres `localhost:5432` (`naukrinearby` / `postgres` / `postgres`), Redis `6379`, Elasticsearch `http://localhost:9200`, MinIO `http://localhost:9000` (`minioadmin` / `minioadmin`). Flyway applies migrations on startup, including `CREATE EXTENSION postgis` and `vector`. Leave `LLM_API_KEY` empty to stay on the offline defaults (OTP code `123456`).

Then the UI:

```bash
cd frontend
cp .env.local.example .env.local
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000). The API is [http://localhost:8080](http://localhost:8080). Health: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health). MinIO console: [http://localhost:9001](http://localhost:9001).

Optional Compose profiles, from `naukrinearby/`:

```bash
# Next.js container on :3000 (API still expected on the host at NEXT_PUBLIC_API_URL)
docker compose --profile frontend up --build frontend

# Prometheus :9090, Grafana :3001 (admin/admin), Langfuse :3002
docker compose --profile observability up -d
```

`NEXT_PUBLIC_API_URL` is inlined at frontend **build** time. The Compose frontend service defaults it to `http://localhost:8080`.

## Local development (without Docker)

Install the same services on the machine and point the app at them. Versions should match the stack above: PostgreSQL 16 with PostGIS and pgvector, Elasticsearch **9.2.8**, Redis 7, MinIO.

1. Create the database and role the app expects (`naukrinearby`, user `postgres`, password `postgres`), or set `SPRING_DATASOURCE_*` to whatever you created. The first Flyway migration runs `CREATE EXTENSION postgis` and `CREATE EXTENSION vector`, so both extensions must already be installed in that Postgres instance.
2. Start Elasticsearch 9.2.8 in single-node mode with security disabled for local dev (`xpack.security.enabled=false`), listening on `http://localhost:9200`. A 9.x server is required: Spring Boot 4’s Java client sends `compatible-with=9` headers that an 8.x cluster rejects.
3. Start Redis on `localhost:6379` and MinIO on `localhost:9000` with access key `minioadmin` and secret `minioadmin` (or override `MINIO_*`).
4. Configure and run the API:

```bash
cd naukrinearby
cp .env.example .env
./gradlew bootRun
```

5. Configure and run the frontend (Node.js 24+):

```bash
cd frontend
cp .env.local.example .env.local
npm install
npm run dev
```

Dev login uses the stub OTP provider: any new phone number, code `123456`, role `CANDIDATE` or `EMPLOYER`.

Useful backend tasks:

```bash
cd naukrinearby
./gradlew test          # needs a Docker daemon — Testcontainers starts Postgres, ES, Redis, and MinIO
./gradlew bootJar
```

Frontend: `npm run lint`, `npm run build`, `npm run start`.

To exercise real extraction instead of the stub, set `EXTRACTION_PROVIDER=gpt5mini` and `LLM_API_KEY` in `.env`. Other live adapters (`EMBEDDING_PROVIDER=remote`, `TRANSLATION_PROVIDER=bhashini`, `OTP_PROVIDER=twilio`, `TWILIO_PROVIDER=twilio`, `VISION_PROVIDER=llm`, `GEOCODER=nominatim`) are the same kind of switch. Embedding width must stay `1024` so vectors match the pgvector column.

## Review-quality eval gate

The Python harness calls the production extraction path (prompt `resume_parse_v3`, the configured LLM adapter, and the same post-processing) through eval-only HTTP endpoints. Those endpoints stay off unless `EVAL_ENABLED=true`, and they require `X-Eval-Key`.

```bash
cd naukrinearby
EVAL_ENABLED=true EVAL_API_KEY=dev-eval-key ./gradlew bootRun
```

Use `EXTRACTION_PROVIDER=stub` for a keyless smoke run, or `gpt5mini` plus `LLM_API_KEY` for a real quality signal.

```bash
cd naukrinearby/eval
pip install -r requirements.txt
export NAUKRI_BASE_URL=http://localhost:8080
export EVAL_API_KEY=dev-eval-key

python run_eval.py                  # skill F1, city accuracy, experience MAE
python run_eval.py --ci             # fail if scores regress vs baseline.json
python run_eval.py --update-baseline
python match_eval.py                # recall@10 and MRR (seed jobs first)
python translation_eval.py          # English → regional phrasing
```

`EVAL_TRACE_ENABLED=true` logs one structured line per extraction (provider, prompt version, latency, JSON validity) for Langfuse or a log pipeline. Details and the match-set workflow are in [`naukrinearby/eval/README.md`](naukrinearby/eval/README.md).

## Monitoring

From `naukrinearby/`:

```bash
docker compose --profile observability up -d
```

| Surface | URL | Notes |
| --- | --- | --- |
| App metrics | `http://localhost:8080/actuator/prometheus` | Also `health`, `info`, `metrics`, `circuitbreakers` |
| Prometheus | `http://localhost:9090` | Scrapes the host app at `host.docker.internal:8080` |
| Grafana | `http://localhost:3001` | User `admin` / `admin`. Dashboard **NaukriNearby — KPIs** is provisioned |
| Langfuse | `http://localhost:3002` | Separate Postgres. Set `NEXTAUTH_SECRET` and `SALT` before any shared use |

Dashboard signals include `search_duration_seconds`, `resume_parse_duration_seconds`, `notifications_sent_total`, `notification_queue_depth`, and `notification_dlq_depth`, plus Resilience4j breaker metrics.

Operational steps for outbox lag, notification DLQ replay, zero-downtime index alias flips, and account erasure are in [`docs/runbooks/operations.md`](docs/runbooks/operations.md).

## CI/CD

[`.github/workflows/ci.yml`](.github/workflows/ci.yml) runs on pushes to `main` and on pull requests:

| Job | What it does |
| --- | --- |
| `build-and-test` | `./gradlew build` in `naukrinearby/`. Testcontainers uses the runner’s Docker daemon (Postgres image built from `docker/postgres`, Elasticsearch 9.2.8, Redis, MinIO). |
| `test-frontend` | Node 24, `npm ci`, `npm run lint`, `npm run build`. |
| `resume-eval-gate` | After the backend build: Compose infra, boot the jar with `EVAL_ENABLED=true` and `EXTRACTION_PROVIDER=stub`, then `python run_eval.py --ci`. |

There is no deploy job in that workflow. Kubernetes manifests for an API tier (`api` profile, workers off) and a worker tier are in [`naukrinearby/k8s/`](naukrinearby/k8s/). Copy `config-and-secrets.example.yaml` and supply real secrets out of band.

## Design notes worth flagging

- **No dual-write.** Job create/update commits Postgres and the outbox together. Elasticsearch is updated only by `OutboxSyncWorker`. A version conflict on `job_id` is treated as already applied.
- **Search stays up when Elasticsearch does not.** `SearchService` catches index failures and answers from PostGIS. The UI shows that degraded path.
- **Pin Elasticsearch to 9.2.x.** The server image in Compose and in tests is `9.2.8`, matching the client that Spring Boot 4 pulls in. Do not point this build at an 8.x cluster.
- **Jackson 3.** Boot 4 auto-configures `tools.jackson.databind.ObjectMapper`. Application code injects that type. JSON annotations remain `com.fasterxml.jackson.annotation`.
- **Provider selection is configuration.** One bean per port (`stub` / `local` / `none` by default). Production swaps are env changes, not new call sites. See [ADR 0002](docs/adr/0002-model-adapter-pattern.md).
- **JWT keys.** If `JWT_PRIVATE_KEY` and `JWT_PUBLIC_KEY` are empty, the process generates an ephemeral RSA keypair. Tokens die on restart. Production must supply PEM keys.
- **Schema.** `spring.jpa.hibernate.ddl-auto=none`. Flyway is the only migration path, and changes are expand/contract so rolling API and worker pods are not broken by a single script. See [ADR 0004](docs/adr/0004-expand-contract-migrations.md).
- **Search index name.** Code uses the alias `jobs`, not `jobs_v1`. Mapping changes go through a reindex and an atomic alias flip ([ADR 0003](docs/adr/0003-alias-flip-reindex.md)).
- **Resumes are personal data.** Objects can be stored with SSE-S3 (`STORAGE_ENCRYPTION=sse`), access is via signed URLs, and account deletion removes the object. Do not log resume text or phone numbers in the clear.
- **Rate limits.** OTP is capped per hour; general API traffic is a fixed window in Redis (default 100 requests/minute per user, or per IP when anonymous).
- **Frontend route gate.** `frontend/src/proxy.ts` only checks that an `nn_session` cookie exists before `/candidate` and `/employer`. Authorization is enforced on the API.
- **Compose does not run the API.** `docker compose up -d` is infra. `bootRun` (or a jar) is a separate process. The frontend container calls the API at `NEXT_PUBLIC_API_URL`, which must be reachable from the browser, not only from inside the container.
- **Workers.** `@Profile("!api")` on the outbox and notification loops. An all-in-one `bootRun` (no `api` profile) runs them in-process, which is what local dev wants.

## Docs

| Document | Contents |
| --- | --- |
| [`naukrinearby/README.md`](naukrinearby/README.md) | Backend run notes and endpoint list |
| [`frontend/README.md`](frontend/README.md) | UI scope, scripts, and the frontend image |
| [`naukrinearby/eval/README.md`](naukrinearby/eval/README.md) | Eval metrics and commands |
| [`docs/adr/0001-transactional-outbox.md`](docs/adr/0001-transactional-outbox.md) | Why the outbox exists |
| [`docs/adr/0002-model-adapter-pattern.md`](docs/adr/0002-model-adapter-pattern.md) | Stub vs live providers |
| [`docs/adr/0003-alias-flip-reindex.md`](docs/adr/0003-alias-flip-reindex.md) | Zero-downtime reindex |
| [`docs/adr/0004-expand-contract-migrations.md`](docs/adr/0004-expand-contract-migrations.md) | Flyway change policy |
| [`docs/runbooks/operations.md`](docs/runbooks/operations.md) | Outbox, DLQ, reindex, erasure |
| [`AGENTS.md`](AGENTS.md) | Repository rules for implementation work |
| [`naukrinearby/.env.example`](naukrinearby/.env.example) | Every backend environment variable |

[`naukrinearby/docs/future-work/README.md`](naukrinearby/docs/future-work/README.md) lists items that were deferred from an earlier milestone. Several of those (frontend, Grafana, Kubernetes manifests, Resilience4j) now exist in this tree; treat that file as a backlog note, not as the current architecture.

## License

The implementation plan for this repository specifies the [MIT License](https://opensource.org/licenses/MIT). A root `LICENSE` file is not in the tree yet; add one before you publish or redistribute the project. Third-party libraries keep their own licenses (Spring, Elasticsearch, PostgreSQL, and the frontend npm dependencies among them).
