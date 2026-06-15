# NaukriNearby — Frontend (Next.js 16)

Frontend for the NaukriNearby hyperlocal job board. Next.js 16 App Router (React 19.2, Turbopack) +
TypeScript, **Tailwind CSS v4 + shadcn/ui** components, **Zod** validation at API boundaries, and
**TanStack React Query** for search/polling. Leaflet/OpenStreetMap powers the map. It talks to the
Spring Boot backend over REST.

## Architecture
- `src/` layout: `src/app` (routes), `src/components` (incl. `ui/` shadcn primitives),
  `src/lib` (api client, auth, utils), `src/types` (Zod schemas + inferred types),
  `src/services` (Zod-parsing API wrappers), `src/hooks` (React Query hooks), `src/proxy.ts` (route gate).
- `src/proxy.ts` replaces `middleware.ts`: a cookie-presence gate over `/candidate` and `/employer`
  (defense-in-depth; real JWT checks stay at the API).

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

Out of scope (MVP): dashboards, notification-preferences UI, employer job-posting UI, offline PWA,
transliteration/voice.

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
