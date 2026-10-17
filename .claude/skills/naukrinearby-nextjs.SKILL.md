---
name: naukrinearby-nextjs
description: >-
  Build the NaukriNearby frontend on Next.js 16 (App Router) with TypeScript and React 19.2. Use
  this skill whenever creating or editing ANY frontend code in this project — pages and layouts
  under app/, Server vs Client Components, data fetching from the Spring Boot API, SEO
  (generateMetadata + JSON-LD JobPosting), the Leaflet map, geolocation, resume upload, OTP auth,
  dashboards, hooks, services, or TypeScript types — even if the user doesn't say "Next.js"
  explicitly. Trigger on App Router, server/client components, 'use client', RSC, SSR/SSG/ISR,
  async params / async searchParams, generateMetadata, route handlers, server actions, proxy.ts,
  Turbopack, Cache Components / "use cache", cacheLife / cacheTag, revalidateTag / updateTag /
  refresh, React Compiler, React 19.2, Suspense/streaming, loading.tsx/error.tsx, TanStack React
  Query, Zod validation, discriminated unions, dynamic import of Leaflet, useGeolocation, or
  next.config standalone output. Also trigger on the Bharat differentiators: Hindi/vernacular UI
  (i18n dictionaries, nn_lang cookie, LanguageSwitcher), offline-first PWA (manifest, service
  worker, IndexedDB apply queue, offline banner), voice search (Web Speech API), trust badges,
  report-job, skill-gap panel, consent/export/delete in the candidate dashboard, employer and
  admin moderation dashboards, and Playwright E2E tests. MANDATE: Next.js 16 App Router + React 19.2 + TypeScript strict on
  Node.js 24 LTS — do NOT generate Pages Router code, do NOT generate Next.js 14/15-era synchronous
  params/cookies/headers access, and keep components Server-first. Pair with
  naukrinearby-tailwind-shadcn for styling.
---

# Next.js 16 (App Router) + TypeScript — NaukriNearby Frontend

You are a senior frontend engineer building NaukriNearby's UI on **Next.js 16 App Router with
React 19.2 and TypeScript (strict)**. The audience is **Tier-2/3 India on slow networks and budget
phones**, so the two ruling constraints are: (1) **job pages must be server-rendered for SEO**
(Google must index "delivery boy Indore"), and (2) **ship as little client JavaScript as possible**.
Server Components are the default tool for both.

> **Why Next.js 16 (not 14).** Next.js 14 is end-of-life and no longer receives security patches;
> 16 is the current stable line (16.2.x as of mid-2026). The migration's load-bearing changes are:
> request APIs (`params`, `searchParams`, `cookies()`, `headers()`) are now **async Promises you
> must `await`**, Turbopack is the **default** bundler, `middleware.ts` is replaced by **`proxy.ts`**,
> and the caching APIs changed (`revalidateTag` needs a profile; `updateTag` is new). All four are
> covered below.

## Runtime & toolchain (pin these)

- **Next.js 16**, **React 19.2** (`react` + `react-dom` at 19.2.x), **TypeScript ≥ 5.1** (strict).
- **Node.js 24 LTS** for dev, CI, and the Docker base image. Next.js 16's floor is Node 20.9, but
  **Node 20 reached end-of-life on 30 Apr 2026** — never ship an EOL runtime. Node 24 is Active LTS
  (matches the rest of this repo's services).
- **Turbopack is the default** for `next dev` and `next build` — there is **no `--turbopack` flag**.
  This project has no custom webpack config, so nothing to migrate; if a dependency injects one,
  `next build` will fail by design (opt out per-build with `--webpack` only as a last resort).

```jsonc
// package.json (no --turbopack flag in Next.js 16; next lint is removed → use eslint directly)
{
  "scripts": {
    "dev": "next dev",
    "build": "next build",
    "start": "next start",
    "lint": "eslint ."
  }
}
```

## Non-negotiables

- **App Router only.** Everything lives under `src/app/`. No `pages/` directory, no
  `getServerSideProps`/`getStaticProps` — those are Pages Router and must not appear.
- **Server Components by default.** Add `'use client'` only when a component needs interactivity,
  React hooks, or browser APIs. A `'use client'` at the top of a page is a smell — push it down to
  the smallest interactive leaf.
- **Request APIs are async.** `params`, `searchParams`, `cookies()`, `headers()`, and `draftMode()`
  are Promises in Next.js 16 — always `await` them. Synchronous access is gone and is a hard error.
- **TypeScript strict.** No `any`. Model API responses as types in `types/`; validate untrusted data
  at the boundary with Zod.
- **The backend is Spring Boot**, reached over REST (`/api/...`). Fetch on the server where possible;
  use Route Handlers (`app/api/*`) only for frontend-owned concerns (e.g. a health check, a BFF
  proxy), not to re-implement the backend.

## Project structure (follow §7.2 exactly)

```
src/
├── app/
│   ├── page.tsx                  # Landing (hero + search) — Server Component
│   ├── (auth)/login · register   # Phone + OTP
│   ├── jobs/page.tsx             # Search results + map
│   ├── jobs/[slug]/page.tsx      # Job detail — SSR + metadata + JSON-LD
│   ├── candidate/                # dashboard · profile · resume/upload · alerts
│   └── employer/                 # dashboard · jobs/new · jobs/[id]/applications
├── components/{jobs,resume,search,notifications,common}/
├── hooks/        # useGeolocation, useJobSearch, useAuth, useResumeUpload
├── services/     # api, authService, jobService, searchService, candidateService
├── lib/          # seo (meta + JSON-LD), constants
├── types/
└── proxy.ts      # route gate (replaces middleware.ts) — see "Route protection" below
```

## Server vs Client Components — the decision

| Use a **Server Component** (default) when… | Use a **Client Component** (`'use client'`) when… |
|---|---|
| Fetching data from the Spring API | Using `useState`/`useEffect`/`useReducer` |
| Rendering job listings/detail for SEO | Handling events (clicks, form input) |
| Reading secrets / server-only config | Using browser APIs (geolocation, `navigator`) |
| Large, non-interactive markup | Rendering the Leaflet map, drag-drop uploader, OTP input |

Compose them: a Server Component page fetches data and passes plain props into small Client
Components for the interactive bits (map, filters, apply button). This keeps the JS bundle small for
budget phones.

## SEO: the reason for SSR (job detail pages)

Job pages are the SEO surface. Render them on the server, set per-page metadata, and embed
**JSON-LD `JobPosting`** structured data so Google shows rich results. **In Next.js 16 `params` is a
`Promise` — `await` it in both `generateMetadata` and the page.**

```tsx
// app/jobs/[slug]/page.tsx  — Server Component (Next.js 16)
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { jobService } from "@/services/jobService";
import { jobPostingJsonLd } from "@/lib/seo";

// params/searchParams are Promises in Next.js 16.
type Props = { params: Promise<{ slug: string }> };

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;                 // ← await, do not destructure synchronously
  const job = await jobService.getBySlug(slug);
  if (!job) return { title: "Job not found — NaukriNearby" };
  return {
    title: `${job.title} in ${job.city} — NaukriNearby`,
    description: `${job.title} at ${job.companyName}, ${job.city}. Apply on NaukriNearby.`,
    openGraph: { title: job.title, description: job.city },
    alternates: { canonical: `/jobs/${job.slug}` },
  };
}

export default async function JobDetailPage({ params }: Props) {
  const { slug } = await params;                 // ← await here too
  const job = await jobService.getBySlug(slug);
  if (!job) notFound();                          // renders app/jobs/[slug]/not-found.tsx
  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jobPostingJsonLd(job)) }}
      />
      <JobDetail job={job} />
      <ApplyButton jobId={job.id} />             {/* small Client Component */}
    </>
  );
}
```

> **Type-safe alternative.** Run `npx next typegen` and use the generated `PageProps` helper:
> `export default async function JobDetailPage({ params }: PageProps<'/jobs/[slug]'>)` — `params`
> and `searchParams` are then typed from the route automatically.

For the search page, `searchParams` is likewise a Promise:

```tsx
// app/jobs/page.tsx — Server Component
export default async function JobsPage(
  { searchParams }: { searchParams: Promise<{ q?: string; lat?: string; lng?: string; radius?: string }> },
) {
  const { q, lat, lng, radius } = await searchParams;
  const results = await jobService.search({ q, lat, lng, radius });
  return <SearchResults results={results} />;
}
```

Validate the JSON-LD against Google's Rich Results Test (it's on the deployment checklist, §22).

## Data fetching

- **Server Components**: call the Spring API with `fetch` and choose a caching policy explicitly —
  `cache: "no-store"` for per-request data (search results), or `next: { revalidate: N, tags: [...] }`
  for cacheable content (job detail). Revalidate with tags on mutations.
- **Parallelize** independent requests to avoid waterfalls: `await Promise.all([...])`.
- **Client interactivity / polling**: use **TanStack React Query** for client hooks
  (`useJobSearch`, and `useResumeUpload` polling `GET /api/resumes/{id}/parse-status` until the LLM
  parse completes). Put the `QueryClientProvider` in a Client Component near the root.
- **Server Actions** are appropriate for simple form mutations (e.g. saving alert preferences) when
  you don't need a separate client cache; keep heavy flows in React Query against the REST API.

```tsx
// services/jobService.ts (server-safe)
export const jobService = {
  async search(params: JobSearchParams): Promise<SearchResponse> {
    const res = await fetch(`${API}/api/jobs/search?${toQuery(params)}`, { cache: "no-store" });
    if (!res.ok) throw new ApiError(res.status);
    return SearchResponseSchema.parse(await res.json());   // Zod at the boundary
  },
};
```

### Next.js 16 caching APIs (what changed)

The fetch-based caching above still works. When you reach for tag revalidation, mind these changes:

- **`revalidateTag(tag, profile)` now requires a `cacheLife` profile** (e.g. `'max'`). The
  single-argument form is removed and errors in TypeScript. It is stale-while-revalidate — readers
  see stale data while it refreshes. Good for the public job feed/detail.
- **`updateTag(tag)`** is a new Server-Actions-only API with **read-your-writes** semantics: expire
  and refresh in the same request so the user sees their change immediately. **This is the frontend
  match for the project's "read-your-writes for the job poster" rule** — after an employer posts/edits
  a job, call `updateTag("employer-jobs-" + userId)` so their dashboard shows it instantly (no stale
  read), while public search stays AP/eventually-consistent through the outbox→ES path.
- **`refresh()`** (Server Actions) refreshes the client router after a mutation.
- **`cacheLife` / `cacheTag` are stable** — drop the old `unstable_` import aliases.
- **`"use cache"`** (Cache Components) is opt-in: set `cacheComponents: true` in `next.config.ts`
  before using `"use cache"` / `cacheLife` / `cacheTag`. Don't sprinkle `"use cache"` without the
  flag — it will error. For this project the explicit `fetch` policy is sufficient; adopt Cache
  Components deliberately, not by default.

```ts
// app/employer/actions.ts — read-your-writes after posting a job
"use server";
import { updateTag } from "next/cache";

export async function publishJob(input: NewJobInput, employerId: string) {
  const job = await jobService.create(input);          // POST to Spring (Postgres = source of truth)
  updateTag(`employer-jobs-${employerId}`);            // poster sees it immediately
  return job;
}
```

## Route protection — `proxy.ts` (replaces `middleware.ts`)

Next.js 16 renames `middleware.ts` → **`proxy.ts`** (exported function `proxy`, runs on the Node.js
runtime, not Edge). Keep it a **lightweight gate** — a cheap presence check that redirects
unauthenticated users; the real JWT verification stays at the Spring API / Server-Component layer.

```ts
// src/proxy.ts (project root of the app)
import { NextResponse, type NextRequest } from "next/server";

export function proxy(request: NextRequest) {
  const token = request.cookies.get("nn_session")?.value;
  const path = request.nextUrl.pathname;
  const guarded = ["/candidate", "/employer", "/admin"].some((p) => path.startsWith(p));
  if (guarded && !token) {
    return NextResponse.redirect(new URL("/login", request.url));
  }
  return NextResponse.next();
}

export const config = { matcher: ["/candidate/:path*", "/employer/:path*", "/admin/:path*"] };
```

(The `@next/codemod upgrade` codemod renames `middleware`→`proxy` automatically if migrating an
existing file. Config flag `skipMiddlewareUrlNormalize` → `skipProxyUrlNormalize`.)

## Leaflet map — must be client-only (common pitfall)

Leaflet touches `window` and cannot render on the server. Load the map via `next/dynamic` with
`ssr: false`, **inside a Client Component** — `ssr: false` dynamic imports are only allowed in
Client Components, so never call this from a Server Component.

```tsx
"use client";
import dynamic from "next/dynamic";
const JobMap = dynamic(() => import("@/components/jobs/JobMapInner"), {
  ssr: false,
  loading: () => <LoadingSkeleton variant="map" />,
});
```

Use OSM tiles (the project chose Leaflet + OpenStreetMap for cost and bundle size — 42KB vs Google
Maps 200KB+, which matters on slow networks).

## Geolocation, OTP, resume upload (Client Components)

```tsx
"use client";
export function useGeolocation() {
  const [coords, setCoords] = useState<{ lat: number; lng: number } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const request = useCallback(() => {
    if (!("geolocation" in navigator)) { setError("Geolocation unsupported"); return; }
    navigator.geolocation.getCurrentPosition(
      (p) => setCoords({ lat: p.coords.latitude, lng: p.coords.longitude }),
      () => setError("Permission denied"),
      { enableHighAccuracy: true, timeout: 10_000 },
    );
  }, []);
  return { coords, error, request };
}
```

For the apply flow, use `useTransition`/`useOptimistic` so the button feels instant on a laggy
network while the request is in flight. **React 19 notes:** `ref` is now a regular prop (no
`forwardRef` needed for new components); the form-state hook is `useActionState` (not the old
`useFormState`), and `useFormStatus` reads pending state inside a form.

## TypeScript discipline

- **Strict mode on.** Treat `any` as a bug. Use `unknown` + a type guard or Zod parse for external data.
- **Discriminated unions** for state with shapes that vary (parse status, request state):

```ts
type ParseState =
  | { status: "idle" }
  | { status: "uploading"; progress: number }
  | { status: "parsing" }
  | { status: "done"; profile: CandidateProfile }
  | { status: "error"; message: string };
```

- **Zod schemas** in `types/` (or co-located) define the API contract once and give you both the
  runtime check and the inferred type: `type Job = z.infer<typeof JobSchema>`.
- **Branded types** for IDs to prevent mixing them up: `type JobId = number & { readonly __brand: "JobId" }`.
- Type event handlers and props precisely; no implicit `any` props.

## Loading, error, empty, and streaming states

- Add `loading.tsx`, `error.tsx` (Client Component, gets `reset`), and `not-found.tsx` per route.
- Wrap slow server data in `<Suspense>` to stream the shell first — important on slow connections.
- Provide real empty states (`EmptyState`) and skeletons (`LoadingSkeleton`) — Tier-2/3 users on 3G
  should never stare at a blank screen.

## Performance for slow networks (this is product-critical)

- Minimize `'use client'`; keep interactive islands small.
- Use `next/image` for any raster assets; lazy-load below the fold. (Next.js 16 changed defaults:
  `minimumCacheTTL` is now 4h and `qualities` defaults to `[75]` — set them explicitly if you need
  other values, and use `images.remotePatterns`, not the removed `images.domains`.)
- Avoid request waterfalls (parallel fetch, Suspense boundaries).
- Configure `output: "standalone"` in `next.config` — the Dockerfile copies `.next/standalone` and
  runs `node server.js`, so this must be set. Build the image on **Node 24 LTS** (`node:24-alpine`).
- The new router does layout deduplication + incremental prefetching automatically (no code
  changes) — fewer bytes per navigation, which helps budget phones.
- Optionally enable the **stable React Compiler** (`reactCompiler: true` + `babel-plugin-react-compiler`)
  to auto-memoize and cut re-renders; it is off by default and adds build time, so measure first.
- The **offline-first PWA** is built (ADR 0009) — see "Bharat differentiators" below.

```ts
// next.config.ts
import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone",
  images: { remotePatterns: [{ protocol: "https", hostname: "**.tile.openstreetmap.org" }] },
  // reactCompiler: true,        // optional: stable in 16, off by default — measure build time
  // cacheComponents: true,      // opt-in before using "use cache" / cacheLife / cacheTag
  // turbopack: { /* options */ } // top-level now (was experimental.turbopack)
};
export default nextConfig;
```

## Bharat differentiators (ADR 0009, plan `docs/plans/differentiators-implementation-plan.md`)

### Vernacular UX (i18n without a framework)
- Dictionaries in `src/i18n/`: `en.ts` exports `const en = {...} as const` and `type Dictionary`
  (deep-widened to `string`); `hi.ts` is `export const hi: Dictionary = {...}`, so a missing Hindi
  key is a **compile error**. `src/i18n/index.ts` exports `LOCALES`, `DEFAULT_LOCALE`, `getDictionary`.
- Locale lives in the `nn_lang` cookie (1 year, `SameSite=Lax`). Server Components read it with
  `await cookies()` via `src/i18n/server.ts#getLocale()`; the root layout sets `<html lang>` and passes
  the dictionary to `I18nProvider` (Client). Client Components call `useT()`.
- `LanguageSwitcher` writes the cookie, calls `router.refresh()`, and (when signed in) `PUT
  /api/auth/me/language` so WhatsApp alerts and the IVR use the same language.
- Never hardcode user-facing strings in components that ship to both languages; add a key.

### Offline-first PWA
- `src/app/manifest.ts` (Metadata Route) + icons in `public/icons/`. Service worker is
  **hand-written** at `public/sw.js` — no `next-pwa`.
- `ServiceWorkerRegistrar` (Client) registers `/sw.js` only in production builds
  (`process.env.NODE_ENV === "production"`) so dev HMR is not cached.
- SW strategies: static `/_next/static/*` + icons cache-first; `GET {API}/api/jobs/search` and
  `GET {API}/api/jobs/{slug}` network-first (4 s timeout) with cache fallback; navigations
  network-first with `/offline` fallback. **Never cache a request with an `Authorization` header**
  or any non-GET. Bump `CACHE_VERSION` when the strategy changes; delete old caches in `activate`.
- Offline apply: `src/lib/offlineQueue.ts` (IndexedDB, no dependency) stores `{ jobId, coverNote,
  queuedAt }`; `ApplyButton` queues when `!navigator.onLine` or the fetch throws a `TypeError`;
  `OfflineSync` replays on `online` and at start. `409` on replay = success. Show a toast either way.
- `OfflineBanner` (Client) reflects `navigator.onLine` with `online`/`offline` listeners.

### Voice search + transliteration
- `useSpeechSearch` wraps `window.SpeechRecognition ?? window.webkitSpeechRecognition` with
  `lang` = `hi-IN` or `en-IN` from the current locale; feature-detect and **hide the mic button**
  when unsupported. Type the API locally (`src/types/speech.d.ts`) — no `any`.
- Hinglish/Devanagari queries are sent as typed; the backend `QueryNormalizer` maps them.

### Trust, fraud, skill gap, consent
- `TrustBadge` renders `trustLevel` (`BUSINESS_VERIFIED`, `PHONE_VERIFIED`, `UNVERIFIED`) and
  `employerSince`. Never show a numeric risk score to candidates.
- `ReportJobButton` (signed-in, any role) → `POST /api/jobs/{id}/report`; `409` = already reported.
- Employer post form shows the moderation outcome: `PENDING_REVIEW` with `riskReasons` from the
  create response.
- `JobTranslationPanel` ("Read this job in …") → `GET /api/jobs/{slug}/translation?lang=` (public,
  server-side Bhashini, cached per job version). Default to the UI locale when it is not English, label
  the result as machine-translated, keep the original visible, and show "not available" when
  `translated=false`. Never call Bhashini from the browser.
- `SkillGapPanel` (candidate only) → `GET /api/candidate/jobs/{id}/skill-gap`; links open official
  portals in a new tab with `rel="noopener noreferrer"`.
- Candidate dashboard holds consent toggles (`/api/candidate/consents`), data export (download the
  JSON blob), and account deletion (confirm dialog).
- `/admin/moderation` is gated in `proxy.ts` like `/candidate` and `/employer`; authorization is
  still enforced by the API (`ROLE_ADMIN`).

### E2E tests (Playwright)
- `frontend/e2e/*.spec.ts`, config `frontend/playwright.config.ts`, Chromium only, `baseURL` from
  `E2E_BASE_URL` (default `http://localhost:3000`), API from `E2E_API_URL`.
- Tests run against the **real stack** (Compose infra + backend jar + `next start`), never mocks.
  Create data through the API (OTP stub code `123456`), use unique phone numbers per test, and
  prefer role/label locators (`getByRole`, `getByLabel`) over CSS.
- Offline behavior: `context.setOffline(true)` after the SW is active (production build only).

## Anti-patterns to fix on sight

| Anti-pattern | Fix |
|---|---|
| `getServerSideProps` / `pages/` (Pages Router) | App Router server components + `fetch` |
| Hardcoded English strings in shared UI | dictionary key + `useT()` / `getDictionary()` |
| `next-pwa` or caching authenticated API responses in the SW | hand-written `public/sw.js`, skip `Authorization` requests |
| Showing a numeric fraud score to candidates | `TrustBadge` only |
| Synchronous `params` / `searchParams` / `cookies()` / `headers()` | `await` them — they are Promises in Next.js 16 |
| `middleware.ts` | `proxy.ts` (export `proxy`, Node.js runtime) |
| `--turbopack` / `--turbo` flag in scripts | remove it — Turbopack is the default in 16 |
| `revalidateTag('jobs')` (single arg) | `revalidateTag('jobs', 'max')`, or `updateTag` for read-your-writes |
| `unstable_cacheLife` / `unstable_cacheTag` imports | import the stable `cacheLife` / `cacheTag` |
| `next lint` in CI | run `eslint` directly (`next lint` removed) |
| `node:20-alpine` base image (EOL) | `node:24-alpine` (Active LTS) |
| `'use client'` at the top of a whole page | push it to the smallest interactive leaf |
| Importing Leaflet in a Server Component / `ssr:false` in an RSC | `next/dynamic` `ssr:false` inside a Client Component |
| Fetching the same data client-side that the server can render | fetch in the Server Component |
| `any` for API responses | Zod schema + inferred type at the boundary |
| Secrets in `NEXT_PUBLIC_*` / client code | keep server-only env vars unprefixed |
| Sequential `await`s causing waterfalls | `Promise.all` / Suspense |
| Client-rendering job detail (no SSR) | Server Component + `generateMetadata` + JSON-LD |
| Blank screen during load | `loading.tsx`, `<Suspense>`, skeletons, empty states |
| `forwardRef` for a brand-new component | React 19: `ref` is a normal prop |

## Quick reference

- Next.js **16 App Router**, React **19.2**, TypeScript strict, **Node 24 LTS**; Server Components first.
- Request APIs are **async** — `await params`, `await searchParams`, `await cookies()`, `await headers()`.
- Turbopack is **default** (no flag); `middleware.ts` → **`proxy.ts`**; `next lint` removed → `eslint`.
- Job pages → SSR + `generateMetadata` + JSON-LD `JobPosting` (SEO is the whole point).
- Data → server `fetch` (explicit cache) + parallelize; React Query for client polling.
- Caching → `revalidateTag(tag,'max')` for the public feed; **`updateTag` for the job poster's
  read-your-writes**; `"use cache"` only behind `cacheComponents: true`.
- Leaflet → dynamic import, `ssr: false`, client-only.
- Validate external data with Zod; model varying state with discriminated unions.
- `output: "standalone"` for the Docker image; minimize client JS for slow networks.
- Styling/components → naukrinearby-tailwind-shadcn.
- Differentiators → i18n dictionaries + `nn_lang` cookie, hand-written SW + IndexedDB apply queue,
  Web Speech voice search, `TrustBadge`/`ReportJobButton`/`SkillGapPanel`, Playwright against the
  real stack.
