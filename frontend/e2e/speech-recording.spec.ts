import { expect, test } from "@playwright/test";

/**
 * The voice-search recorder fallback end to end (ADR 0009): Chromium's fake microphone (a tone),
 * granted without a prompt, with the Web Speech API removed. Checks that the browser-made WAV is what
 * the server accepts. Its own file because launch options force a separate worker.
 */
test.use({
  launchOptions: { args: ["--use-fake-device-for-media-stream", "--use-fake-ui-for-media-stream"] },
  permissions: ["microphone"],
});

test("the fallback records, encodes 16 kHz WAV in the browser, and the server accepts it", async ({ page }) => {
  await page.addInitScript(() => {
    const w = window as unknown as Record<string, unknown>;
    delete w.SpeechRecognition;
    delete w.webkitSpeechRecognition;
  });
  await page.goto("/jobs");
  await page.getByRole("button", { name: "Search by voice" }).click();
  await expect(page.getByRole("button", { name: "Stop listening" })).toBeVisible();
  await page.waitForTimeout(1500);

  const response = page.waitForResponse((res) => res.url().includes("/api/speech/transcribe"));
  await page.getByRole("button", { name: "Stop listening" }).click();

  const res = await response;
  // The server answers 400 to anything but 16 kHz mono 16-bit WAV, so 200 proves the browser encoding.
  expect(res.status()).toBe(200);
  // The stub provider hears nothing, so the user is told to try again or type.
  expect(await res.json()).toMatchObject({ text: "", provider: "stub" });
  await expect(page.getByRole("status").filter({ hasText: "Could not hear you" })).toBeAttached();
});
