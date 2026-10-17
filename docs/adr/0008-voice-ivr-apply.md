# ADR 0008 — Voice-First (IVR) Apply

## Status
Accepted (2026-10-17).

## Context
WhatsApp-first apply (G1) reaches users who will not install an app. A further segment has low
literacy or a feature phone: they can make a call but cannot comfortably type. They hear about jobs
through alerts, posters, or word of mouth with a job number.

## Decision
- **Twilio Programmable Voice webhooks** return TwiML:
  - `POST /api/webhooks/twilio/voice` — bilingual greeting (हिंदी first, then English) and main menu.
  - `POST /api/webhooks/twilio/voice/gather/{menu|job|confirm}` — handles each `<Gather>` result. The
    step is a path segment, not a query parameter, so the URL Twilio signs is the plain request URL.
  - Responses are `application/xml;charset=UTF-8`; without the explicit charset the Hindi prompts are
    garbled.
- **Menu:** `1` apply to a job by number (DTMF digits then `#`, or spoken digits), confirm with `1`;
  `2` hear the three best jobs near the caller's saved profile location; `9` repeat.
- **Speech + DTMF** via `<Gather input="dtmf speech" language="hi-IN">`, using Twilio's built-in
  recognition and `<Say language="hi-IN">` voices. Bhashini ASR/TTS is a later adapter.
- **State** lives in Redis keyed by `CallSid` (TTL 15 min). Each webhook is stateless otherwise,
  so voice traffic scales with the API tier.
- **Same domain services as WhatsApp:** `AuthService.getOrCreateCandidate(callerPhone)` and
  `ApplicationService.apply` (idempotent: a second apply says "already applied").
- **Same security as WhatsApp:** every voice webhook is verified with `X-Twilio-Signature`; caller
  phone numbers are redacted in logs.
- A confirmation is sent over WhatsApp after a successful IVR apply when the user has not opted out.

## Consequences
- A caller can apply in about 30 seconds without reading anything.
- Speech recognition quality for regional accents is Twilio's; job numbers via DTMF are the
  reliable path, and the menu always offers it.
- Testable offline: integration tests post form-encoded webhook payloads with a valid signature
  and assert on the TwiML.
