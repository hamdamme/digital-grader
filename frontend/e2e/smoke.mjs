import { chromium, expect } from "@playwright/test";
import { join } from "node:path";
import { execFileSync } from "node:child_process";
const base = process.env.SMOKE_BASE_URL;
const password = process.env.SMOKE_PASSWORD;
const output = process.env.SMOKE_OUTPUT;
const realGrading = process.env.SMOKE_REAL_GRADING === "true";
const browser = await chromium.launch({
  headless: true,
  ...(process.env.SMOKE_CHROME_PATH
    ? { executablePath: process.env.SMOKE_CHROME_PATH }
    : {}),
});
const context = await browser.newContext({
  baseURL: base,
  viewport: { width: 1440, height: 1000 },
});
const page = await context.newPage();
const errors = [];
page.on("pageerror", (error) => errors.push(error.message));
async function login(email) {
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
}
try {
  // Instructor provisioning is an existing administrative API, not public registration.
  const auth = await context.request.post("/api/v1/auth/login", {
    data: { email: "admin@smoke.local", password },
  });
  expect(auth.ok()).toBeTruthy();
  const token = (await auth.json()).accessToken;
  const teacher = await context.request.post("/api/v1/admin/users", {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      email: "teacher@smoke.local",
      password,
      fullName: "Jordan Instructor",
      role: "INSTRUCTOR",
    },
  });
  expect(teacher.status()).toBe(201);
  await page.goto("/");
  await page.screenshot({ path: join(output, "sign-in.png"), fullPage: true });
  await page
    .getByRole("button", { name: "New student? Create an account" })
    .click();
  await page.getByLabel("Full name").fill("Casey Student");
  await page.getByLabel("Email", { exact: true }).fill("student@smoke.local");
  await page.getByLabel("Password", { exact: true }).fill(password);
  await page
    .getByRole("button", { name: "Create account", exact: true })
    .click();
  await expect(
    page.getByText("Your student account is ready. Sign in to continue."),
  ).toBeVisible();
  await login("student@smoke.local");
  await expect(
    page.getByRole("heading", { name: "No cohorts yet" }),
  ).toBeVisible();
  const profile = await page.locator(".teacher-profile small").textContent();
  const studentId = profile.match(/#(\d+)/)[1];
  await page.getByRole("button", { name: "Sign out" }).click();
  await login("teacher@smoke.local");
  await page
    .getByRole("link", { name: "Manage cohort", exact: false })
    .first()
    .click();
  await page.getByLabel("Cohort name").fill("Java automation lab");
  await page.getByLabel("Start date").fill("2026-10-01");
  await page.getByLabel("End date").fill("2027-03-31");
  await page
    .getByRole("button", { name: "Create cohort", exact: true })
    .click();
  await page.getByLabel("Student account ID").fill(studentId);
  await page.getByRole("button", { name: "Enroll student" }).click();
  await expect(page.getByText(`Casey Student · #${studentId}`)).toBeVisible();
  await page.getByRole("link", { name: "Assignments", exact: false }).click();
  await page.getByRole("button", { name: "New assignment" }).click();
  await page
    .getByLabel("Title", { exact: true })
    .fill("Collections and validation");
  await page
    .getByLabel("Description")
    .fill(
      "Test collection behavior and invalid input. Submit your Maven project as a ZIP.",
    );
  await page.getByRole("button", { name: "Save assignment" }).click();
  await expect(
    page.getByRole("heading", { name: "Collections and validation" }),
  ).toBeVisible();
  await page.screenshot({
    path: join(output, "assignments.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "Sign out" }).click();
  await login("student@smoke.local");
  await page.getByRole("link", { name: "Open assignment" }).click();
  await page
    .getByLabel("Project ZIP")
    .setInputFiles(join(output, "project.zip"));
  await page.getByRole("button", { name: "Submit project" }).click();
  if (!realGrading) {
    await expect(page.getByRole("heading", { name: "Waiting for grading" })).toBeVisible();
  }
  await page.screenshot({
    path: join(output, "queued-submission.png"),
    fullPage: true,
  });
  await page.getByRole("button", { name: "Sign out" }).click();
  await login("teacher@smoke.local");
  await page.getByRole("link", { name: "Overview" }).click();
  await expect(
    page.getByRole("cell", { name: "Casey Student" }).first(),
  ).toBeVisible();
  if (!realGrading) {
    await expect(page.getByRole("cell", { name: "QUEUED", exact: true })).toBeVisible();
  }
  await page.screenshot({
    path: join(output, "teacher-overview.png"),
    fullPage: true,
  });
  await page.getByRole("link", { name: /Open submission/ }).click();
  const submissionId = new URL(page.url()).hash.match(/review\/(\d+)/)[1];
  if (realGrading) {
    await expect(page.getByRole("heading", { name: "Test results" })).toBeVisible({ timeout: 240000 });
    await expect(page.getByText(/deliberatelyFails/).first()).toBeVisible();
    // Read the result through the authenticated API; never insert result fixtures.
    const teacherAuth = await context.request.post("/api/v1/auth/login", {
      data: { email: "teacher@smoke.local", password },
    });
    expect(teacherAuth.ok()).toBeTruthy();
    const teacherToken = (await teacherAuth.json()).accessToken;
    const response = await context.request.get(`/api/v1/submissions/${submissionId}/result`, {
      headers: { Authorization: `Bearer ${teacherToken}` },
    });
    expect(response.ok()).toBeTruthy();
    const result = await response.json();
    expect(result.testsTotal).toBe(2);
    expect(result.testsPassed).toBe(1);
    expect(result.testsFailed).toBe(1);
    expect(Number(result.coverageLinePct)).toBeGreaterThan(0);
  } else {
  // Interface fixtures remain separate from real grading acceptance.
  execFileSync(
    "docker",
    [
      "exec",
      "-i",
      process.env.SMOKE_DB_CONTAINER,
      "psql",
      "-U",
      "smoke",
      "-d",
      "smoke",
      "-v",
      "ON_ERROR_STOP=1",
    ],
    {
      input: `UPDATE submissions SET status='GRADED' WHERE id=${submissionId};
      INSERT INTO grading_results(submission_id,build_success,tests_total,tests_passed,tests_failed,coverage_line_pct,coverage_branch_pct,score,rubric_breakdown)
      VALUES (${submissionId},true,2,1,1,60,50,70,'{}');
      INSERT INTO test_case_results(grading_result_id,test_name,status,duration_ms,error_message)
      SELECT id,'rejectNull','FAILED',10,'Expected IllegalArgumentException' FROM grading_results WHERE submission_id=${submissionId};
      INSERT INTO security_outbox(submission_id,status,completed_at) VALUES (${submissionId},'COMPLETE',now());`,
      stdio: ["pipe", "ignore", "pipe"],
    },
  );
  await page.getByRole("button", { name: "Refresh status" }).click();
  await expect(
    page.getByRole("heading", { name: "Test results" }),
  ).toBeVisible();
  }
  await page.getByRole("tab", { name: "Review history" }).click();
  await page.getByLabel("Reviewed score").fill("85");
  await page
    .getByLabel("Reason for change")
    .fill("Reviewed the test evidence with the student.");
  await page.getByRole("button", { name: "Save score override" }).click();
  await expect(page.getByText("85/100")).toBeVisible();
  await expect(
    page.getByText("Reviewed the test evidence with the student."),
  ).toBeVisible();
  await page.screenshot({
    path: join(output, "review-workbench.png"),
    fullPage: true,
  });
  await page.getByRole("link", { name: "Overview" }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({
    path: join(output, "mobile-overview.png"),
    fullPage: true,
  });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBeTruthy();
  await expect(page.getByRole("button", { name: "Sign out" })).toBeVisible();
  await expect(page.getByLabel("CURRENT COHORT")).toBeVisible();
  await page.reload();
  await expect(
    page.getByRole("heading", { name: "Sign in", exact: true }),
  ).toBeVisible();
  expect(errors).toEqual([]);
  console.log(
    (realGrading ? "PASS: real gVisor grading and report retrieval; " : "PASS: stored report fixtures; ") + "registration, login, cohort creation, enrollment, assignment creation, ZIP upload, teacher list, score override, mobile layout, reload sign out.",
  );
} finally {
  await context.close();
  await browser.close();
}

