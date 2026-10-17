import { expect, test } from "@playwright/test";

import { apiLogin, postJob, randomIndianPoint, switchLanguage, waitUntilSearchable } from "./helpers";

test.describe("vernacular search", () => {
  test("home page switches between English and Hindi", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Jobs near you, in your language");

    await switchLanguage(page, "हिंदी");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("आपके पास की नौकरियां, आपकी भाषा में");
    await expect(page.locator("html")).toHaveAttribute("lang", "hi-IN");

    // The choice survives a reload (nn_lang cookie).
    await page.reload();
    await expect(page.getByRole("link", { name: "नौकरी खोजें", exact: true })).toBeVisible();

    await switchLanguage(page, "English");
    await expect(page.getByRole("heading", { level: 1 })).toHaveText("Jobs near you, in your language");
  });

  test("a Hinglish query finds an English job near the user", async ({ page, context, request }) => {
    const point = randomIndianPoint();
    const employer = await apiLogin(request, "EMPLOYER");
    const title = `Electrician ${Date.now()}`;
    const job = await postJob(request, employer, { title, description: "House wiring and repairs.", ...point });
    await waitUntilSearchable(request, job.id, point);

    await context.grantPermissions(["geolocation"]);
    await context.setGeolocation({ latitude: point.lat, longitude: point.lng });

    await page.goto("/jobs");
    await page.getByRole("textbox", { name: /find jobs/i }).fill("bijli mistri ka kaam chahiye");
    await page.getByRole("button", { name: "Search", exact: true }).click();
    await expect(page.getByRole("link", { name: title })).toBeVisible();

    await expect(page.getByText("Phone verified").first()).toBeVisible();
  });
});
