# ADR 0001 — Transactional Outbox for Postgres → Elasticsearch

## Status
Accepted.

## Context
Jobs are the source of truth in Postgres but are searched from Elasticsearch. A dual write (save to
PG, then index to ES) is not atomic: a crash between the two leaves the stores inconsistent, and ES
downtime would either block writes or silently drop index updates.

## Decision
Writes that must reach ES are recorded as rows in an `outbox_events` table inside the **same DB
transaction** as the business change. A background `OutboxSyncWorker` polls `PENDING` rows and indexes
them into ES with an **external-version-guarded idempotent upsert** keyed by `job_id`:

- At-least-once delivery (poll + retry) + idempotent upsert ⇒ effectively-once.
- A version conflict (ES already has a newer doc) is treated as success (409 → `PROCESSED`).
- Events that fail `MAX_RETRIES` times are marked `FAILED`; a nightly `reconcile()` job alerts on them.

ES downtime is survivable: events stay `PENDING` and drain on recovery. Reads degrade to a PostGIS
`ST_DWithin` query in the meantime (see `SearchService`).

## Consequences
- No dual-write inconsistency; ES is eventually consistent with PG (sub-second under normal load).
- The worker is split out via `@Profile("!api")` so it scales independently of the API tier.
- Requires monitoring of outbox lag and `FAILED` count (see runbook).
