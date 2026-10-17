# ADR 0007 — Trust Signals, Fraud Scoring, and Moderation

## Status
Accepted (2026-10-17).

## Context
Indian job boards are a common vector for scams ("pay ₹500 registration fee", "security deposit
refundable", off-platform WhatsApp numbers). Our users are often first-time job seekers on low-end
phones, so a scam posting does real harm and destroys trust in the product. We need:

1. a way to flag risky postings before they are shown,
2. visible trust signals for candidates, and
3. a human review path,

without breaking the outbox rule (no dual-write) or slowing the write path.

## Decision
- **Rule-based, explainable risk score.** `JobRiskScorer` returns `score` (0–100) and `reasons`.
  Rules: payment-request phrases in English, Hinglish, and Devanagari (data files under
  `resources/trust/`); off-platform contact details in the description (phone numbers, `wa.me`
  links); salary far above the category band; unverified employer; brand-new employer; shouting
  (excess capitals/exclamation). Rules are cheap (no network), so scoring is synchronous.
- **Moderation gates the index, not the write.** Every job is committed to Postgres with
  `moderation_status` = `APPROVED` (score below `naukri.trust.review-threshold`) or
  `PENDING_REVIEW`. The status rides in the outbox payload. `OutboxSyncWorker` deletes the ES
  document for anything not `APPROVED`; the PostGIS fallback and job-detail endpoint filter the
  same way (the owning employer and admins can still read it).
- **Community reports.** `POST /api/jobs/{id}/report` (signed-in users, one per user per job). At
  `naukri.trust.report-threshold` distinct reports, the job moves to `PENDING_REVIEW` via the outbox.
- **Admin review.** `/api/admin/moderation/**` (ROLE_ADMIN) lists the queue and records
  `APPROVED` / `REJECTED`. Admins are bootstrapped from `NAUKRI_ADMIN_PHONES`.
- **Trust levels.** `UNVERIFIED` → `PHONE_VERIFIED` (OTP login, always true for employers who log
  in) → `BUSINESS_VERIFIED` (GSTIN passes `GstVerifier`). The default verifier is offline: GSTIN
  format plus the mod-36 check digit. A GSP/government API adapter can replace it via
  `naukri.trust.gst-verifier`.
- Candidates see a trust badge and employer tenure; they never see the numeric risk score.

## Consequences
- No ML model to train, host, or explain; every flag lists its reasons for the reviewer.
- A false positive delays a posting until review; it is never lost. A false negative is caught by
  reports.
- Expand-only migration (`V5`): new nullable/defaulted columns and a `job_reports` table.
- A future ML scorer plugs in behind the same interface and can run async, moving jobs to review
  through the outbox.
