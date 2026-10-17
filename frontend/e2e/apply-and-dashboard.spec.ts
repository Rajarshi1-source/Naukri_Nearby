import { expect, test } from "@playwright/test";

import { API_URL, apiLogin, postJob, randomIndianPoint, randomPhone } from "./helpers";

test("candidate logs in with OTP, sees trust signals, applies, and finds it on the dashboard", async ({
  page,
  request,
}) => {
  const employer = await apiLogin(request, "EMPLOYER");
  const title = `Delivery Partner ${Date.now()}`;
  const job = await postJob(request, employer, {
    title,
    description: "Deliver groceries on your own bike.",
    skillsRequired: ["Driving", "Tally"],
    ...randomIndianPoint(),
  });

  // OTP login through the UI (stub provider accepts 123456).
  const phone = randomPhone();
  await page.goto(`/login?next=/jobs/${job.slug}`);
  await page.getByLabel("Phone number").fill(phone);
  await page.getByRole("button", { name: "Job seeker" }).click();
  await page.getByRole("button", { name: "Send OTP" }).click();
  await page.getByLabel("One-time password").fill("123456");
  await page.getByRole("button", { name: "Verify & continue" }).click();

  await expect(page).toHaveURL(new RegExp(`/jobs/${job.slug}$`));
  await expect(page.getByRole("heading", { level: 1 })).toHaveText(title);
  await expect(page.getByText("Phone verified")).toBeVisible();
  await expect(page.getByText(/Never pay money to get a job/)).toBeVisible();

  // Skill gap for a candidate with no profile: both skills missing, official portals linked.
  await expect(page.getByText("Your skills for this job")).toBeVisible();
  await expect(page.getByRole("link", { name: /SWAYAM/ }).first()).toHaveAttribute("href", "https://swayam.gov.in");

  // "Read this job in": the request goes to our API (never to Bhashini from the browser). CI and local
  // runs use the passthrough stub, so the panel must say no translation is available rather than
  // present English as one.
  const translation = page.waitForResponse((r) => r.url().includes(`/api/jobs/${job.slug}/translation?lang=ta`));
  await page.getByRole("combobox", { name: "Read this job in" }).click();
  await page.getByRole("option", { name: "தமிழ்" }).click();
  expect((await translation).status()).toBe(200);
  await expect(page.getByText("Translation is not available right now.")).toBeVisible();

  await page.getByRole("button", { name: "Apply now" }).click();
  await expect(page.getByText("Application submitted!").first()).toBeVisible();

  await page.goto("/candidate");
  await expect(page.getByRole("link", { name: title })).toBeVisible();

  // DPDP: withdraw WhatsApp-alert consent; the ledger records it.
  const alerts = page.getByRole("checkbox", { name: /Send me job alerts on WhatsApp/ });
  await expect(alerts).toBeChecked();
  await alerts.click();
  await expect(page.getByText("Your choice is saved.")).toBeVisible();
  await expect(alerts).not.toBeChecked();

  const access = await page.evaluate(() => window.localStorage.getItem("nn_access"));
  const consents = await request.get(`${API_URL}/api/candidate/consents`, {
    headers: { Authorization: `Bearer ${access}` },
  });
  const whatsapp = ((await consents.json()) as { purpose: string; granted: boolean | null }[]).find(
    (c) => c.purpose === "WHATSAPP_ALERTS",
  );
  expect(whatsapp?.granted).toBe(false);
});

test("report button asks anonymous users to log in", async ({ page, request }) => {
  const employer = await apiLogin(request, "EMPLOYER");
  const job = await postJob(request, employer, {
    title: `Shop Helper ${Date.now()}`,
    description: "Help at a kirana store.",
    ...randomIndianPoint(),
  });
  await page.goto(`/jobs/${job.slug}`);
  await page.getByRole("button", { name: "Report job" }).click();
  await expect(page).toHaveURL(/\/login\?next=/);
});
