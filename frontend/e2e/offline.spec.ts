import { expect, test } from "@playwright/test";

import { API_URL, apiLogin, postJob, randomIndianPoint, useSession } from "./helpers";

/**
 * Offline-first PWA (ADR 0009). Needs a production build (`next start`): the service worker is only
 * registered in production.
 */
test("job pages work offline and an offline apply is sent on reconnect", async ({ browser, request }) => {
  const employer = await apiLogin(request, "EMPLOYER");
  const title = `Tailor ${Date.now()}`;
  const job = await postJob(request, employer, {
    title,
    description: "Stitching and alterations at a boutique.",
    ...randomIndianPoint(),
  });
  const candidate = await apiLogin(request, "CANDIDATE");

  const context = await browser.newContext();
  await useSession(context, candidate);
  const page = await context.newPage();

  // First visit installs the worker; the second load is controlled by it and caches the page.
  await page.goto(`/jobs/${job.slug}`);
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });
  await page.reload();
  await expect.poll(() => page.evaluate(() => navigator.serviceWorker.controller !== null)).toBe(true);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(title);

  await context.setOffline(true);
  await page.reload();
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(title);
  await expect(page.getByRole("status").filter({ hasText: /You are offline/ })).toBeVisible();

  await page.getByRole("button", { name: "Apply now" }).click();
  await expect(page.getByText("Application queued — will send when online")).toBeVisible();

  // Back online: OfflineSync replays the queued apply.
  await context.setOffline(false);
  await expect
    .poll(
      async () => {
        const res = await request.get(`${API_URL}/api/candidate/applications`, {
          headers: { Authorization: `Bearer ${candidate.accessToken}` },
        });
        const body = (await res.json()) as { content: { jobId: number }[] };
        return body.content.some((a) => a.jobId === job.id);
      },
      { timeout: 20_000 },
    )
    .toBe(true);

  await context.close();
});

test("the app is installable: manifest and icons are served", async ({ request, baseURL }) => {
  const manifest = await request.get(`${baseURL}/manifest.webmanifest`);
  expect(manifest.ok()).toBeTruthy();
  const body = (await manifest.json()) as { display: string; icons: { src: string }[] };
  expect(body.display).toBe("standalone");
  for (const icon of body.icons) {
    expect((await request.get(`${baseURL}${icon.src}`)).ok()).toBeTruthy();
  }
});
