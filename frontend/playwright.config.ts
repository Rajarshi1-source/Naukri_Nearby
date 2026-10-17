import { defineConfig, devices } from "@playwright/test";

/**
 * E2E tests run against the real stack (ADR 0009 / AGENTS.md): Compose infra + the backend jar +
 * `next start` (a production build, so the service worker is active). Nothing is mocked.
 *
 *   E2E_BASE_URL    frontend   (default http://localhost:3000)
 *   E2E_API_URL     backend    (default http://localhost:8080)
 *   E2E_ADMIN_PHONE a phone listed in the backend's NAUKRI_ADMIN_PHONES (default 9000000001)
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : [["list"]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    locale: "en-IN",
  },
  projects: [
    { name: "mobile-chromium", use: { ...devices["Pixel 7"] } },
  ],
});
