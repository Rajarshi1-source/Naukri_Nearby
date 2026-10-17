import { randomInt } from "node:crypto";

import { expect, type APIRequestContext, type BrowserContext, type Page } from "@playwright/test";

export const API_URL = process.env.E2E_API_URL ?? "http://localhost:8080";
export const ADMIN_PHONE = process.env.E2E_ADMIN_PHONE ?? "9000000001";
export const OTP = process.env.E2E_OTP ?? "123456";

/** A fresh Indian mobile number per call, so OTP rate limits (5/hour/phone) never trip. */
export function randomPhone(): string {
  return "9" + String(randomInt(1_000_000_000)).padStart(9, "0");
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  role: string;
  phone: string;
}

export async function apiLogin(
  request: APIRequestContext,
  role: "CANDIDATE" | "EMPLOYER",
  phone = randomPhone(),
): Promise<Session> {
  const sent = await request.post(`${API_URL}/api/auth/send-otp`, { data: { phone } });
  expect(sent.ok(), await sent.text()).toBeTruthy();
  const res = await request.post(`${API_URL}/api/auth/verify-otp`, { data: { phone, code: OTP, role } });
  expect(res.ok(), await res.text()).toBeTruthy();
  return { ...(await res.json()), phone } as Session;
}

/** Puts an API session into the browser the same way the app does after an OTP login. */
export async function useSession(context: BrowserContext, session: Session): Promise<void> {
  const base = new URL(process.env.E2E_BASE_URL ?? "http://localhost:3000");
  await context.addCookies([{ name: "nn_session", value: "1", domain: base.hostname, path: "/" }]);
  await context.addInitScript((s) => {
    window.localStorage.setItem("nn_access", s.accessToken);
    window.localStorage.setItem("nn_refresh", s.refreshToken);
    window.localStorage.setItem("nn_role", s.role);
  }, session);
}

export interface JobInput {
  title: string;
  description: string;
  category?: string;
  skillsRequired?: string[];
  lat: number;
  lng: number;
  city?: string;
}

export async function postJob(request: APIRequestContext, employer: Session, job: JobInput) {
  const res = await request.post(`${API_URL}/api/jobs`, {
    headers: { Authorization: `Bearer ${employer.accessToken}` },
    data: {
      category: "OTHER",
      skillsRequired: ["Hard work"],
      salaryMin: 12000,
      salaryMax: 18000,
      salaryType: "MONTHLY",
      experienceRequiredMonths: 0,
      city: "Lucknow",
      state: "Uttar Pradesh",
      pincode: "226001",
      radiusKm: 10,
      ...job,
    },
  });
  expect(res.ok(), await res.text()).toBeTruthy();
  return (await res.json()) as { id: number; slug: string; moderationStatus: string; riskReasons: string[] };
}

/**
 * Waits until the outbox has synced a job into the search index. Search responses are cached by
 * query, so each poll varies `size` (part of the cache key) to avoid re-reading a cached miss.
 */
export async function waitUntilSearchable(
  request: APIRequestContext,
  jobId: number,
  point: { lat: number; lng: number },
): Promise<void> {
  await expect
    .poll(
      async () => {
        const size = 20 + randomInt(30);
        const res = await request.get(
          `${API_URL}/api/jobs/search?lat=${point.lat}&lng=${point.lng}&radius=5&size=${size}`,
        );
        const body = (await res.json()) as { results: { id: number }[] };
        return body.results.some((r) => r.id === jobId);
      },
      { timeout: 30_000, intervals: [1_000] },
    )
    .toBe(true);
}

/** A random point inside India, so this run's jobs are the only ones within a few km. */
export function randomIndianPoint(): { lat: number; lng: number } {
  // node:crypto rather than Math.random (CodeQL js/insecure-randomness); 1e-6 degree steps.
  return { lat: 21 + randomInt(5_000_000) / 1e6, lng: 75 + randomInt(6_000_000) / 1e6 };
}

export async function switchLanguage(page: Page, label: "English" | "हिंदी") {
  await page.getByRole("combobox", { name: /language|भाषा/i }).click();
  await page.getByRole("option", { name: label }).click();
}
