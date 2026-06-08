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
- Replace the explicit retry/fallback helpers with a circuit-breaker library once one targets Spring
  Boot 4 (the `resilience4j-spring-boot4` starter does not exist yet on Maven Central). Native Spring
  Framework 7 `@Retryable` / `@ConcurrencyLimit` can also be adopted for declarative retries.

## Bharat differentiators (G-features)
- G1 voice-first / IVR application flow, G2 vernacular UX, G4+ trust & verification signals.

## Platform / ops
- Next.js frontend, Kubernetes manifests, Grafana dashboards, Langfuse prompt/version tracing,
  blue/green ES reindex via the versioned index + alias already in place.
