# Future Work (deferred from the Backend MVP)

These were intentionally scoped out of the MVP (see the plan's "Scope" section). The architecture
already accommodates them behind existing seams (provider adapters, the outbox, config flags).

## Integrations — swap stubs for real providers
- **Twilio WhatsApp + Verify**: implement `WhatsAppSender` / `OtpProvider` with the Twilio SDK and
  select via `naukri.twilio.provider` / `naukri.otp.provider`. The webhook signature path is already real.
- **Bhashini translation**: implement `TranslationProvider` (HTTP) and select via `naukri.translation.provider`;
  the English fallback in `TranslationService` stays as the resilience backstop.
- **Real embeddings (bge-m3)**: add a `RemoteEmbeddingProvider` (`EmbeddingProvider`) selected by
  `naukri.embedding.provider=remote`. The local deterministic provider remains the offline default.

## Resilience hardening
- Done: external providers use `resilience4j-spring-boot4` (2.4.0, verified on Spring Boot 4.1.1)
  `@Retry` + `@CircuitBreaker`. Next step: bulkhead the resume-parse path with Framework 7
  `@ConcurrencyLimit` so parse load can't starve WhatsApp delivery.

## Bharat differentiators (G-features)
- G1 voice-first / IVR application flow, G2 vernacular UX, G4+ trust & verification signals.

## Platform / ops
- Next.js frontend, Kubernetes manifests, Grafana dashboards, Langfuse prompt/version tracing,
  blue/green ES reindex via the versioned index + alias already in place.
