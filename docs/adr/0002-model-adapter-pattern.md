# ADR 0002 — Provider-Agnostic Model/External Adapters

## Status
Accepted.

## Context
The system depends on several external services that differ by environment and may be swapped:
LLM extraction, embeddings, vision OCR, translation, WhatsApp send, OTP, and geocoding. We must run
and test the whole system **offline with no API keys**, while allowing real providers in production
without code changes.

## Decision
Every external capability sits behind a small interface (e.g. `LlmExtractionProvider`,
`EmbeddingProvider`, `TranslationProvider`, `WhatsAppSender`, `OtpProvider`, `GeocodingProvider`).
Each interface has:

- a **stub/local default** annotated `@ConditionalOnProperty(..., matchIfMissing = true)`, and
- one or more **real adapters** selected by the same `naukri.*.provider` property.

Only one bean is active per interface, chosen by config. Real adapters call HTTP endpoints via
`RestClient` and are wrapped with Resilience4j (`@Retry` + `@CircuitBreaker`); service-layer fallbacks
(translation→English, ES→PostGIS, LLM→manual fill) provide graceful degradation.

## Consequences
- `./gradlew test` and local dev need zero secrets (stubs are the default).
- Switching to a live provider is a config/env change (`EMBEDDING_PROVIDER=remote`, etc.).
- Each new external dependency follows the same pattern; the dimension contract for embeddings
  (`naukri.embedding.dims=1024`) is enforced at the adapter to protect the pgvector column.
