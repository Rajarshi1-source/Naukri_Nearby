# ADR 0003 — Zero-Downtime Reindex via Alias Flip

## Status
Accepted.

## Context
The ES `jobs` index has a fixed mapping (geo_point, custom Hindi analyzer). Changing the analyzer or
field mappings requires a full reindex, which we must do without search downtime or code changes that
hardcode a physical index name.

## Decision
Application code only ever reads/writes the **alias** `jobs` (`naukri.search.index`), never a physical
index. `ElasticsearchIndexBootstrap` creates `jobs_v1` with `aliases: { jobs: {} }`. To change the
mapping/analyzer:

1. Create `jobs_v2` with the new settings (declared in `ElasticsearchIndexBootstrap`).
2. `POST _reindex` from `jobs_v1` → `jobs_v2` (or replay the outbox into v2).
3. Atomically flip the alias: `POST _aliases` with `remove jobs_v1` + `add jobs_v2` in one call.
4. Drop `jobs_v1` after verification.

## Consequences
- Searches never see a missing/empty index during reindex (the flip is atomic).
- The Hindi analyzer is declared explicitly in index settings (not relying on the built-in), so v2
  changes are reproducible.
- The runbook documents the exact commands.
