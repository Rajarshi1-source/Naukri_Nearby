import { expect, test } from "@playwright/test";

import { API_URL, apiLogin, postJob, randomIndianPoint } from "./helpers";

/**
 * Bhashini speech (ADR 0009) against the real stack with the default stub speech provider: the job
 * page's Listen button, and the voice-search mic falling back to recording where the browser has no
 * Web Speech API.
 */
test.describe("speech", () => {
  test("job page shows a Listen button that falls back to the server's audio", async ({ page, request }) => {
    const employer = await apiLogin(request, "EMPLOYER");
    const title = `Tailor ${Date.now()}`;
    const job = await postJob(request, employer, {
      title,
      description: "Stitching and alterations at a boutique.",
      ...randomIndianPoint(),
    });

    // No browser voices, so the button has to use the endpoint; the stub provider has no audio (204).
    await page.addInitScript(() => {
      Object.defineProperty(window.speechSynthesis, "getVoices", { value: () => [] });
    });
    await page.goto(`/jobs/${job.slug}`);
    await expect(page.getByRole("heading", { level: 1, name: title })).toBeVisible();

    const listen = page.getByRole("button", { name: "Listen", exact: true });
    await expect(listen).toBeVisible();
    const audio = page.waitForResponse((res) => res.url().includes(`/api/jobs/${job.slug}/audio`));
    await listen.click();
    expect((await audio).status()).toBe(204);
    await expect(page.getByText("Audio is not available for this job right now.")).toBeVisible();
  });

  test("the audio endpoint hides jobs that are not public", async ({ request }) => {
    const res = await request.get(`${API_URL}/api/jobs/no-such-job-${Date.now()}/audio?lang=hi`);
    expect(res.status()).toBe(404);
  });

  test("voice search uses the browser recognizer where it exists", async ({ page }) => {
    await page.goto("/jobs");
    await expect(page.getByRole("button", { name: "Search by voice" })).toHaveAttribute(
      "data-voice-mode",
      "browser",
    );
  });

  test("without the Web Speech API the mic falls back to recording for the server", async ({ page }) => {
    await page.addInitScript(() => {
      const w = window as unknown as Record<string, unknown>;
      delete w.SpeechRecognition;
      delete w.webkitSpeechRecognition;
    });
    await page.goto("/jobs");
    const mic = page.getByRole("button", { name: "Search by voice" });
    await expect(mic).toBeVisible();
    await expect(mic).toHaveAttribute("data-voice-mode", "server");
  });
});
