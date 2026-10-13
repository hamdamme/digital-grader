import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate, useParams } from "react-router-dom";
import { api, message } from "./api";
import { useResource, LoadState } from "./hooks";
import { date } from "./Overview";
import { useWorkspace } from "./Workspace";
import { validateSubmission } from "../lib/submission";
import type { Assignment, Submission } from "./types";
export function SubmitPage() {
  const { id } = useParams();
  const { user } = useWorkspace();
  const assignment = useResource<Assignment>(`/assignments/${id}`);
  const attempts = useResource<Submission[]>(
    user.role === "STUDENT" ? `/assignments/${id}/mysubmissions` : null,
  );
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();
  if (user.role !== "STUDENT") return <Navigate to="/assignments" replace />;
  if (!assignment.data)
    return <LoadState error={assignment.error} retry={assignment.reload} />;
  if (!attempts.data)
    return <LoadState error={attempts.error} retry={attempts.reload} />;
  const a = assignment.data;
  const closed = !!a.dueDate && Date.parse(a.dueDate) <= Date.now();
  const exhausted = attempts.data.length >= a.maxAttempts;
  async function upload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const error = validateSubmission(file);
    if (error) {
      setError(error);
      return;
    }
    setBusy(true);
    setError("");
    try {
      const body = new FormData();
      body.append("file", file!);
      const accepted = await api.request<{ submissionId: number }>(
        `/assignments/${id}/submissions`,
        { method: "POST", body },
      );
      navigate(`/review/${accepted.submissionId}`);
    } catch (error) {
      setError(message(error));
      attempts.reload();
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Link className="studio-back" to="/assignments">
        ‹ Back to assignments
      </Link>
      <div className="studio-page-heading">
        <div>
          <p className="studio-kicker">ASSIGNMENT #{id}</p>
          <h1>{a.title}</h1>
          <p>
            {a.dueDate ? `Due ${date(a.dueDate)}` : "No deadline"} ·{" "}
            {attempts.data.length}/{a.maxAttempts} attempts used
          </p>
        </div>
      </div>
      <div className="live-columns">
        <section className="live-card">
          <h2>Your project</h2>
          <p className="preserve-text">{a.description}</p>
          <form className="live-form" onSubmit={upload}>
            <label>
              Project ZIP
              <input
                type="file"
                accept=".zip"
                disabled={busy || closed || exhausted}
                onChange={(e) => {
                  setFile(e.target.files?.[0] ?? null);
                  setError("");
                }}
              />
            </label>
            <p className="notice">
              Up to 50 MiB. Include pom.xml and source files. Uploads wait in
              the queue until a grading worker is available.
            </p>
            {closed && <p role="status">The deadline has passed.</p>}
            {exhausted && <p role="status">All attempts have been used.</p>}
            {error && (
              <p role="alert" className="live-alert">
                {error}
              </p>
            )}
            <button
              className="studio-button"
              disabled={busy || closed || exhausted}
            >
              {busy ? "Uploading…" : "Submit project"}
            </button>
          </form>
        </section>
        <section className="live-card">
          <h2>Previous attempts</h2>
          {attempts.data.length ? (
            <ul>
              {attempts.data.map((s) => (
                <li key={s.id}>
                  <Link to={`/review/${s.id}`}>
                    Attempt {s.attemptNumber} · {s.status} ·{" "}
                    {date(s.submittedAt)}
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <p>No submissions yet.</p>
          )}
        </section>
      </div>
    </>
  );
}
