// Captures the README screenshots from a running stack seeded with demo data.
// Usage (from frontend/): node scripts/screenshots.mjs  — writes ../docs/screenshots/*.png
import { fileURLToPath } from "node:url";

import { chromium, devices } from "@playwright/test";

const BASE = process.env.E2E_BASE_URL ?? "http://localhost:3000";
const API = process.env.E2E_API_URL ?? "http://localhost:8080";
const OUT = new URL("../../docs/screenshots/", import.meta.url);
const LUCKNOW = { latitude: 26.8467, longitude: 80.9462 };

async function login(phone, role) {
  await fetch(`${API}/api/auth/send-otp`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ phone }) });
  const r = await fetch(`${API}/api/auth/verify-otp`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ phone, code: "123456", role }) });
  return r.json();
}

async function context(browser, { session, locale = "en", mobile = false } = {}) {
  const ctx = await browser.newContext({
    ...(mobile ? devices["Pixel 7"] : { viewport: { width: 1280, height: 860 } }),
    geolocation: LUCKNOW,
    permissions: ["geolocation"],
  });
  const host = new URL(BASE).hostname;
  const cookies = [{ name: "nn_lang", value: locale, domain: host, path: "/" }];
  if (session) {
    cookies.push({ name: "nn_session", value: "1", domain: host, path: "/" });
    await ctx.addInitScript((s) => {
      localStorage.setItem("nn_access", s.accessToken);
      localStorage.setItem("nn_refresh", s.refreshToken);
      localStorage.setItem("nn_role", s.role);
    }, session);
  }
  await ctx.addCookies(cookies);
  return ctx;
}

async function shot(ctx, path, file, { full = false, waitFor } = {}) {
  const page = await ctx.newPage();
  await page.goto(`${BASE}${path}`);
  if (waitFor) await page.getByText(waitFor).first().waitFor();
  await page.waitForTimeout(1500); // map tiles and fonts
  await page.screenshot({ path: fileURLToPath(new URL(file, OUT)), fullPage: full });
  await page.close();
  console.log("saved", file);
}

const browser = await chromium.launch();
const candidate = await login("9811100010", "CANDIDATE");
const shady = await login("9811100002", "EMPLOYER");
const admin = await login("9000000001", "CANDIDATE");

const jobs = await (await fetch(`${API}/api/jobs/search?lat=${LUCKNOW.latitude}&lng=${LUCKNOW.longitude}&radius=20&size=20`)).json();
const accountant = jobs.results.find((j) => j.title.startsWith("Accountant"));

await shot(await context(browser, { mobile: true, locale: "hi" }), "/", "home-hindi-mobile.png");
await shot(await context(browser), "/jobs?q=bijli%20mistri%20ka%20kaam", "search-hinglish.png", { waitFor: "Electrician" });
await shot(await context(browser, { session: candidate }), `/jobs/${accountant.slug}`, "job-detail-skill-gap.png", { full: true, waitFor: "Your skills for this job" });
await shot(await context(browser, { session: shady }), "/employer", "employer-under-review.png", { full: true, waitFor: "Under review" });
await shot(await context(browser, { session: admin }), "/admin/moderation", "admin-moderation.png", { full: true, waitFor: "Risk score" });

await browser.close();
