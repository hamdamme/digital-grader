import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { App } from "../App";
import { api } from "./api";
const cohort = {
  id: 2,
  name: "Java class",
  startDate: "2026-10-01",
  endDate: "2027-01-01",
  studentCount: 1,
};
const assignment = {
  id: 3,
  cohortId: 2,
  title: "Collections exercise",
  description: "Submit tests for collections.",
  dueDate: null,
  maxAttempts: 3,
  rubricJson: {
    weights: { tests: 60, coverage: 20, codeQuality: 20 },
    coverageThreshold: 70,
    qualityChecks: [],
  },
};
const submission = {
  id: 4,
  assignmentId: 3,
  studentId: 1,
  attemptNumber: 1,
  status: "QUEUED",
  submittedAt: "2026-10-12T12:00:00Z",
  score: null,
};
const result = {
  submissionId: 4,
  buildSuccess: true,
  score: 70,
  effectiveScore: 70,
  testsTotal: 1,
  testsPassed: 0,
  testsFailed: 1,
  testsFlaky: 0,
  coverageLinePct: 60,
  coverageBranchPct: 50,
  securityStatus: "PENDING",
  testCaseResults: [
    {
      testName: "rejectNull",
      status: "FAILED",
      durationMs: 10,
      errorMessage: "Expected rejection",
      stackTrace: null,
    },
  ],
  securityFindings: [],
  hints: [],
  overrides: [],
};
const response = (value: unknown, status = 200) =>
  status === 204
    ? new Response(null, { status })
    : new Response(JSON.stringify(value), { status });
let role = "STUDENT";
let handler: (
  path: string,
  options: RequestInit,
) => Response | Promise<Response>;
beforeEach(() => {
  api.clear();
  role = "STUDENT";
  handler = (path) => {
    if (path === "/cohorts/mine") return response([cohort]);
    if (path === "/assignments?cohortId=2") return response([assignment]);
    if (path === "/assignments/3") return response(assignment);
    if (path === "/assignments/3/mysubmissions") return response([]);
    if (path === "/submissions/4") return response(submission);
    if (path === "/submissions?cohortId=2") return response([]);
    return response({ message: "Fixture route not found" }, 404);
  };
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, options: RequestInit) => {
      const path = url.replace("/api/v1", "");
      if (path === "/auth/login")
        return response({
          accessToken: "fixture",
          refreshToken: "fixture",
          role,
        });
      if (path === "/auth/me")
        return response({
          id: 1,
          fullName: "Casey Student",
          email: "s@fixture.local",
          role,
        });
      if (path === "/auth/logout") return response(null, 204);
      return handler(path, options);
    }),
  );
});
afterEach(() => {
  api.clear();
  vi.unstubAllGlobals();
});
async function open(path: string, teacher = false) {
  role = teacher ? "INSTRUCTOR" : "STUDENT";
  await api.login("s", "password");
  render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}
it("signs in and shows scoped assignments without sample data", async () => {
  render(
    <MemoryRouter>
      <App />
    </MemoryRouter>,
  );
  await userEvent.type(screen.getByLabelText("Email"), "s@fixture.local");
  await userEvent.type(screen.getByLabelText("Password"), "fixture-password");
  await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
  expect(
    await screen.findByRole("heading", { name: "Collections exercise" }),
  ).toBeVisible();
  expect(screen.queryByText("Alex Lee")).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole("button", { name: "Sign out" }));
  expect(await screen.findByRole("heading", { name: "Sign in" })).toBeVisible();
});
it("uploads an archive and shows a real queued state", async () => {
  const original = handler;
  handler = (path, options) => {
    if (path === "/assignments/3/submissions") {
      expect(options.body).toBeInstanceOf(FormData);
      expect((options.body as FormData).get("file")).toBeInstanceOf(File);
      return response({ submissionId: 4, status: "QUEUED" }, 202);
    }
    return original(path, options);
  };
  await open("/submit/3");
  await userEvent.upload(
    await screen.findByLabelText("Project ZIP"),
    new File(["fixture"], "project.zip", { type: "application/zip" }),
  );
  await userEvent.click(screen.getByRole("button", { name: "Submit project" }));
  expect(
    await screen.findByRole("heading", { name: "Waiting for grading" }),
  ).toBeVisible();
  expect(screen.queryByText("recorded score")).not.toBeInTheDocument();
});
it("shows API failure without pretending the upload succeeded", async () => {
  const original = handler;
  handler = (path, options) =>
    path.endsWith("/submissions")
      ? response({ message: "Maximum submission attempts reached" }, 409)
      : original(path, options);
  await open("/submit/3");
  await userEvent.upload(
    await screen.findByLabelText("Project ZIP"),
    new File(["fixture"], "project.zip"),
  );
  await userEvent.click(screen.getByRole("button", { name: "Submit project" }));
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Maximum submission attempts reached",
  );
  expect(
    screen.queryByRole("heading", { name: "Waiting for grading" }),
  ).not.toBeInTheDocument();
});
it("shows pending scan status, denies hints until complete, and renders errors as text", async () => {
  const original = handler;
  handler = (path, options) =>
    path === "/submissions/4"
      ? response({ ...submission, status: "GRADED" })
      : path.endsWith("/result")
        ? response({
            ...result,
            testCaseResults: [
              {
                ...result.testCaseResults[0],
                errorMessage: "<img src=x onerror=alert(1)>",
              },
            ],
          })
        : original(path, options);
  await open("/review/4");
  await screen.findByRole("heading", { name: "Test results" });
  await userEvent.click(screen.getByText("rejectNull"));
  expect(screen.getByText("<img src=x onerror=alert(1)>")).toBeVisible();
  expect(document.querySelector("img")).toBeNull();
  screen.getByRole("tab", { name: "Tests" }).focus();
  await userEvent.keyboard("{ArrowRight}");
  expect(screen.getByRole("tab", { name: "Security" })).toHaveFocus();
  expect(screen.getByText("Scan status: PENDING")).toBeVisible();
  await userEvent.click(screen.getByRole("tab", { name: "Guidance" }));
  expect(
    screen.getByRole("button", { name: "Request guidance" }),
  ).toBeDisabled();
  await userEvent.click(screen.getByRole("tab", { name: "Review history" }));
  expect(
    screen.queryByRole("button", { name: "Save score override" }),
  ).not.toBeInTheDocument();
});
it("saves a teacher override with its expected score and reason", async () => {
  const original = handler;
  let saved = false;
  handler = (path, options) => {
    if (path === "/submissions/4")
      return response({ ...submission, status: "GRADED" });
    if (path.endsWith("/result"))
      return response({
        ...result,
        effectiveScore: saved ? 85 : 70,
        overrides: saved
          ? [
              {
                id: 1,
                originalScore: 70,
                score: 85,
                reason: "Reviewed evidence",
                instructorName: "Teacher",
                createdAt: "2026-10-12T12:00:00Z",
              },
            ]
          : [],
      });
    if (path.endsWith("/overrides")) {
      expect(JSON.parse(options.body as string)).toEqual({
        score: 85,
        expectedScore: 70,
        reason: "Reviewed evidence",
      });
      saved = true;
      return response(null, 204);
    }
    return original(path, options);
  };
  await open("/review/4", true);
  await screen.findByRole("heading", { name: "Test results" });
  await userEvent.click(screen.getByRole("tab", { name: "Review history" }));
  await userEvent.clear(screen.getByLabelText("Reviewed score"));
  await userEvent.type(screen.getByLabelText("Reviewed score"), "85");
  await userEvent.type(
    screen.getByLabelText("Reason for change"),
    "Reviewed evidence",
  );
  await userEvent.click(
    screen.getByRole("button", { name: "Save score override" }),
  );
  expect(await screen.findByText("85/100")).toBeVisible();
  expect(await screen.findByText("Reviewed evidence")).toBeVisible();
});
it("handles unavailable cohort loading with an explicit retry", async () => {
  let fail = true;
  const original = handler;
  handler = (path, options) =>
    path === "/cohorts/mine" && fail
      ? response({ message: "Service unavailable" }, 503)
      : original(path, options);
  await open("/assignments");
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "Service unavailable",
  );
  fail = false;
  await userEvent.click(screen.getByRole("button", { name: "Try again" }));
  expect(
    await screen.findByRole("heading", { name: "Collections exercise" }),
  ).toBeVisible();
});
it("renders student trends without replacing unfinished security scans with zero", async () => {
  const original = handler;
  handler = (path, options) =>
    path === "/submissions?cohortId=2"
      ? response([
          {
            ...submission,
            assignmentTitle: "Collections exercise",
            studentName: "Casey",
            status: "GRADED",
            score: 70,
            coverageLinePct: 60,
            findingCount: null,
            securityStatus: "PENDING",
          },
        ])
      : original(path, options);
  await open("/progress");
  expect(
    await screen.findByRole("img", {
      name: "Score over submissions by submission, oldest to newest",
    }),
  ).toBeVisible();
  expect(
    screen.getByRole("img", {
      name: "Line coverage (%) by submission, oldest to newest",
    }),
  ).toBeVisible();
  expect(screen.getByText("No completed measurements yet.")).toBeVisible();
});
