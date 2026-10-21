import { useState, type FormEvent, type ReactNode } from "react";
import { Link, Navigate } from "react-router-dom";
import { api, message, post } from "./api";
import { LoadState, useResource } from "./hooks";
import { NoCohort, date } from "./Overview";
import { useWorkspace } from "./Workspace";
import type { Assignment, User } from "./types";

export function ActionForm({
  children,
  action,
  label,
  success = "Saved.",
}: {
  children: ReactNode;
  action: (data: FormData) => Promise<unknown>;
  label: string;
  success?: string;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [done, setDone] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setBusy(true);
    setError("");
    setDone(false);
    try {
      await action(data);
      form.reset();
      setDone(true);
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <form className="live-form" onSubmit={submit}>
      <fieldset disabled={busy}>{children}</fieldset>
      {error && (
        <p role="alert" className="live-alert">
          {error}
        </p>
      )}
      {done && <p role="status">{success}</p>}
      <button className="studio-button" disabled={busy}>
        {busy ? "Saving…" : label}
      </button>
    </form>
  );
}
export function CohortsPage() {
  const { user, cohort, reload } = useWorkspace();
  const detail = useResource<{ members: User[] }>(
    cohort && user.role !== "STUDENT" ? `/cohorts/${cohort.id}` : null,
  );
  if (user.role === "STUDENT") return <Navigate to="/assignments" replace />;
  return (
    <>
      <div className="studio-page-heading">
        <div>
          <p className="studio-kicker">CLASSROOM SETUP</p>
          <h1>Manage cohorts</h1>
          <p>Create a cohort and enroll existing student accounts.</p>
        </div>
      </div>
      <div className="live-columns">
        <section className="live-card">
          <h2>Create cohort</h2>
          <ActionForm
            label="Create cohort"
            action={async (data) => {
              await api.request("/cohorts", post(Object.fromEntries(data)));
              reload();
            }}
          >
            <label>
              Cohort name
              <input name="name" required maxLength={255} />
            </label>
            <label>
              Start date
              <input name="startDate" type="date" required />
            </label>
            <label>
              End date
              <input name="endDate" type="date" required />
            </label>
          </ActionForm>
        </section>
        {cohort && (
          <section className="live-card">
            <h2>{cohort.name}</h2>
            <ActionForm
              label="Enroll student"
              success="Student enrolled."
              action={async (data) => {
                await api.request(
                  `/cohorts/${cohort.id}/members`,
                  post({ studentIds: [Number(data.get("studentId"))] }),
                );
                detail.reload();
                reload();
              }}
            >
              <label>
                Student account ID
                <input
                  name="studentId"
                  type="number"
                  min="1"
                  step="1"
                  required
                />
              </label>
              <p className="notice">
                Students can find their account ID below their name in the
                sidebar.
              </p>
            </ActionForm>
            <h3>Enrolled students</h3>
            {!detail.data ? (
              <LoadState error={detail.error} retry={detail.reload} />
            ) : detail.data.members.length === 0 ? (
              <p>No students enrolled yet.</p>
            ) : (
              <ul>
                {detail.data.members.map((m) => (
                  <li key={m.id}>
                    {m.fullName} · #{m.id}
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}
      </div>
    </>
  );
}
export function AssignmentsPage() {
  const { cohort, user } = useWorkspace();
  const assignments = useResource<Assignment[]>(
    cohort ? `/assignments?cohortId=${cohort.id}` : null,
  );
  const [create, setCreate] = useState(false);
  if (!cohort) return <NoCohort />;
  const teacher = user.role !== "STUDENT";
  return (
    <>
      <div className="studio-page-heading">
        <div>
          <p className="studio-kicker">{cohort.name}</p>
          <h1>Assignments</h1>
          <p>
            {teacher
              ? "Set expectations and review student work."
              : "Choose an assignment and upload your Maven project."}
          </p>
        </div>
        {teacher && (
          <button
            className="studio-button"
            onClick={() => setCreate((v) => !v)}
          >
            {create ? "Close form" : "New assignment"}
          </button>
        )}
      </div>
      {create && (
        <section className="live-card">
          <h2>Create assignment</h2>
          <ActionForm
            label="Save assignment"
            action={async (data) => {
              const weights = {
                tests: Number(data.get("tests")),
                coverage: Number(data.get("coverage")),
                codeQuality: Number(data.get("quality")),
              };
              if (
                weights.tests + weights.coverage + weights.codeQuality !==
                100
              )
                throw new Error("Rubric weights must total 100.");
              await api.request(
                "/assignments",
                post({
                  cohortId: cohort.id,
                  title: data.get("title"),
                  description: data.get("description"),
                  dueDate: data.get("dueDate")
                    ? new Date(String(data.get("dueDate"))).toISOString()
                    : null,
                  maxAttempts: Number(data.get("maxAttempts")),
                  buildCommand: "mvn -B test",
                  rubricJson: {
                    weights,
                    coverageThreshold: Number(data.get("threshold")),
                    qualityChecks: data.getAll("checks"),
                  },
                }),
              );
              setCreate(false);
              assignments.reload();
            }}
          >
            <label>
              Title
              <input name="title" required maxLength={255} />
            </label>
            <label>
              Description
              <textarea name="description" rows={3} maxLength={20000} />
            </label>
            <div className="live-columns">
              <label>
                Due date (local time, optional)
                <input name="dueDate" type="datetime-local" />
              </label>
              <label>
                Maximum attempts
                <input
                  name="maxAttempts"
                  type="number"
                  required
                  min="1"
                  max="20"
                  defaultValue="3"
                />
              </label>
            </div>
            <p>Rubric weights must total 100.</p>
            <div className="live-columns">
              <label>
                Tests weight
                <input
                  name="tests"
                  type="number"
                  min="0"
                  max="100"
                  required
                  defaultValue="60"
                />
              </label>
              <label>
                Coverage weight
                <input
                  name="coverage"
                  type="number"
                  min="0"
                  max="100"
                  required
                  defaultValue="20"
                />
              </label>
              <label>
                Code quality weight
                <input
                  name="quality"
                  type="number"
                  min="0"
                  max="100"
                  required
                  defaultValue="20"
                />
              </label>
              <label>
                Minimum coverage for credit (%)
                <input
                  aria-describedby="coverage-credit-help"
                  name="threshold"
                  type="number"
                  min="0"
                  max="100"
                  required
                  defaultValue="70"
                />
              </label>
            </div>
            <p id="coverage-credit-help">Coverage below this minimum earns no coverage credit. At or above it, the measured percentage is used. Test and quality credit are unchanged.</p>
            <label className="check-label">
              <input
                name="checks"
                type="checkbox"
                value="checkstyle"
                defaultChecked
              />
              Checkstyle
            </label>
            <label className="check-label">
              <input
                name="checks"
                type="checkbox"
                value="spotbugs"
                defaultChecked
              />
              SpotBugs
            </label>
          </ActionForm>
        </section>
      )}
      {!assignments.data ? (
        <LoadState error={assignments.error} retry={assignments.reload} />
      ) : assignments.data.length === 0 ? (
        <section className="live-card">
          <h2>No assignments yet</h2>
          <p>
            {teacher
              ? "Create an assignment to get started."
              : "Your instructor has not added an assignment yet."}
          </p>
        </section>
      ) : (
        <div className="live-assignment-grid">
          {assignments.data.map((a) => (
            <article className="live-card" key={a.id}>
              <span className="studio-kicker">ASSIGNMENT #{a.id}</span>
              <h2>{a.title}</h2>
              <p className="preserve-text">
                {a.description || "No description provided."}
              </p>
              <p>
                {a.dueDate ? `Due ${date(a.dueDate)}` : "No deadline"} ·{" "}
                {a.maxAttempts} attempts
              </p>
              <p className="notice">
                Tests {a.rubricJson.weights.tests}% · Coverage{" "}
                {a.rubricJson.weights.coverage}% · Quality{" "}
                {a.rubricJson.weights.codeQuality}%
              </p>
              {teacher ? (
                <Link className="studio-button secondary" to="/results">
                  View submissions
                </Link>
              ) : (
                <Link className="studio-button" to={`/submit/${a.id}`}>
                  Open assignment
                </Link>
              )}
            </article>
          ))}
        </div>
      )}
    </>
  );
}

