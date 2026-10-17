import { expect, test } from "@playwright/test";

import { ADMIN_PHONE, apiLogin, randomPhone, useSession } from "./helpers";

test("a scam posting is held for review and published only after admin approval", async ({ browser, request }) => {
  const title = `Data Entry Work ${Date.now()}`;

  // Employer posts through the dashboard.
  const employerContext = await browser.newContext();
  await useSession(employerContext, await apiLogin(request, "EMPLOYER", randomPhone()));
  const employerPage = await employerContext.newPage();
  await employerPage.goto("/employer");
  await employerPage.getByLabel("Job title").fill(title);
  await employerPage.getByLabel("Description").fill("Ghar baithe earn daily. Pay registration fee Rs 500 to join.");
  await employerPage.getByRole("button", { name: "Lucknow" }).click();
  await employerPage.getByRole("button", { name: "Publish job" }).click();

  await expect(employerPage.getByText("Your job is under review before it appears in search.").first()).toBeVisible();
  await expect(employerPage.getByText("Asks candidates to pay a fee or deposit").first()).toBeVisible();

  // Admin approves it from the queue.
  const adminContext = await browser.newContext();
  await useSession(adminContext, await apiLogin(request, "CANDIDATE", ADMIN_PHONE));
  const adminPage = await adminContext.newPage();
  await adminPage.goto("/admin/moderation");
  await expect(adminPage.getByRole("heading", { name: "Moderation queue" })).toBeVisible();
  const row = adminPage.getByRole("listitem").filter({ hasText: title });
  await expect(row).toBeVisible();
  await row.getByRole("button", { name: "Approve" }).click();
  await expect(adminPage.getByText("Decision saved.")).toBeVisible();
  await expect(row).toHaveCount(0);

  // Now it is live for the employer, and public.
  await employerPage.reload();
  const live = employerPage.getByRole("link", { name: title });
  await expect(live).toBeVisible();
  const href = await live.getAttribute("href");
  const anonymous = await browser.newPage();
  await anonymous.goto(href!);
  await expect(anonymous.getByRole("heading", { level: 1 })).toHaveText(title);

  await employerContext.close();
  await adminContext.close();
});
