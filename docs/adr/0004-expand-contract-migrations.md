# ADR 0004 — Expand/Contract Database Migrations

## Status
Accepted.

## Context
Flyway migrations run on startup against a shared Postgres while multiple app/worker pods are rolling.
A destructive change (drop/rename column) applied while old pods still run will break them.

## Decision
Schema changes follow **expand → migrate → contract**, never a breaking change in one step:

1. **Expand**: add the new nullable column/table (backward compatible). Deploy code that writes both
   old and new.
2. **Migrate**: backfill data; switch reads to the new shape.
3. **Contract**: in a *later* release (after all pods run the new code), drop the old column.

Migrations are forward-only and idempotent where possible; `spring.jpa.hibernate.ddl-auto=none` —
Flyway is the only schema authority. Postgres extensions (PostGIS, pgvector) are created in the first
migration and are required by the custom image.

## Consequences
- Rolling deploys are safe; no coordinated downtime for schema changes.
- A breaking change spans two releases by design.
- Vector/geo columns that JPA can't map are managed by SQL migrations + native queries.
