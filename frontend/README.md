# NaukriNearby — Frontend (Next.js 16)

Frontend for the NaukriNearby hyperlocal job board. Next.js 16 App Router (React 19.2, Turbopack) +
TypeScript, **Tailwind CSS v4 + shadcn/ui** components, **Zod** validation at API boundaries, and
**TanStack React Query** for search/polling. Leaflet/OpenStreetMap powers the map. It talks to the
Spring Boot backend over REST.

## Architecture
- `src/` layout: `src/app` (routes), `src/components` (incl. `ui/` shadcn primitives),
  `src/lib` (api client, auth, utils), `src/types` (Zod schemas + inferred types),
  `src/services` (Zod-parsing API wrappers), `src/hooks` (React Query hooks), `src/proxy.ts` (route gate).
- `src/proxy.ts` replaces `middleware.ts`: a cookie-presence gate over `/candidate`, `/employer` and
  `/admin` (defense-in-depth; real JWT checks stay at the API).
- `src/i18n` holds typed English and Hindi dictionaries (ADR 0009). The `nn_lang` cookie picks the
  language; Server Components read it with `await cookies()`, Client Components use `useT()`.

## Features in this MVP
- Phone + OTP login/register (candidate or employer), with JWT access/refresh handling and a
  transparent 401 refresh-retry.
- Hyperlocal job search: browser geolocation, radius slider, category filter, results list, and a
  Leaflet map showing your location + search radius. Surfaces the backend `fellBackToPostgis` flag
  as a "search degraded" banner.
- SSR job detail pages (`/jobs/[slug]`) with `schema.org/JobPosting` JSON-LD and SEO metadata for
  Google indexing.
- Resume upload (PDF/photo) with async parse-status polling, then the auto-filled profile.
- One-tap apply (`POST /api/applications`, 409 handled as "already applied") and a save/bookmark
  toggle (`/api/candidate/saved-jobs/{jobId}`).

- **Vernacular UI:** English / हिंदी switcher in the header, saved to the account so WhatsApp
  alerts and the IVR use the same language.
- **Voice search** (Web Speech API, `hi-IN` / `en-IN`) and Hinglish/Devanagari queries
  ("bijli mistri", "ड्राइवर") that the backend maps to job terms.
- **Offline-first PWA:** installable manifest, a hand-written service worker (`public/sw.js`) that
  caches the app shell, job pages and public search results, and an IndexedDB queue that sends
  applications made offline when the connection returns.
- **Trust and safety:** employer trust badges, a report-job flow, and a "never pay for a job" notice.
- **Skill gap:** on a job page, a signed-in candidate sees missing skills with links to free official
  learning portals.
- **Dashboards:** candidate (applications, saved jobs, recommendations, WhatsApp alert settings,
  consent toggles, data download, account deletion), employer (business profile with GSTIN, post a
  job with moderation feedback, applicants with status changes), and admin moderation queue.

## Prerequisites
- Node 24+ (LTS)
- The backend running and reachable (default `http://localhost:8080`). See `../naukrinearby`.

## Quick start (local dev)
```bash
cd frontend
cp .env.local.example .env.local   # set NEXT_PUBLIC_API_URL if not :8080
npm install
npm run dev                        # http://localhost:3000
```
Dev OTP: the backend's stub OTP provider accepts code `123456`.

## Scripts
- `npm run dev` — dev server
- `npm run build` — production build (Next standalone output)
- `npm run start` — run the production build
- `npm run lint` — ESLint flat config (`eslint .`, extends `next/core-web-vitals` + `next/typescript`)
- `npm run test:e2e` — Playwright E2E against a running stack (see below)

## E2E tests (Playwright)
The specs in `e2e/` drive the real app: Compose infra, the backend jar, and a production frontend
(`npm run build && npx next start`, so the service worker is active). Nothing is mocked.
```bash
# backend started with NAUKRI_ADMIN_PHONES=9000000001 (the moderation spec logs in as that admin)
npx playwright install chromium
npm run test:e2e
```
`E2E_BASE_URL`, `E2E_API_URL` and `E2E_ADMIN_PHONE` override the defaults (`:3000`, `:8080`,
`9000000001`). CI runs the same suite in the `e2e` job.

## Configuration
- `NEXT_PUBLIC_API_URL` — backend base URL. Inlined at **build time** (it is a `NEXT_PUBLIC_*`
  value), so Docker builds pass it as a build arg.

## Docker
Built via the root compose under the `frontend` profile:
```bash
# from naukrinearby/
docker compose --profile frontend up --build frontend
```
The container serves on `:3000`. The browser calls the backend directly at `NEXT_PUBLIC_API_URL`
(default `http://localhost:8080`), so run the backend on the host. For local development prefer
`npm run dev` over the container so SSR fetches resolve to your host backend.

## Notes
- CORS for `http://localhost:3000` is already configured on the backend (`CORS_ALLOWED_ORIGINS`).
- Backend errors follow RFC 9457 ProblemDetail; the API client surfaces `detail`/`title` as messages.
