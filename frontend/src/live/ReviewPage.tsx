import { useRef, useState, type KeyboardEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { api, message, post } from "./api";
import { useResource, LoadState } from "./hooks";
import { date } from "./Overview";
import { useWorkspace } from "./Workspace";
import { ActionForm } from "./ManagePages";
import type { Assignment, Result, Submission } from "./types";
const tabs = ["Tests", "Security", "Guidance", "Review history"] as const;
export function ReviewPage() {
  const { id } = useParams();
  const submission = useResource<Submission>(`/submissions/${id}`, true);
  const assignment = useResource<Assignment>(
    submission.data ? `/assignments/${submission.data.assignmentId}` : null,
  );
  if (!submission.data)
    return <LoadState error={submission.error} retry={submission.reload} />;
  const s = submission.data;
  return (
    <>
      <Link className="studio-back" to="/results">
        ‹ Back to submissions
      </Link>
      <div className="studio-page-heading">
        <div>
          <p className="studio-kicker">SUBMISSION #{id}</p>
          <h1>{assignment.data?.title ?? "Submission details"}</h1>
          <p>
            Student #{s.studentId} · Attempt {s.attemptNumber} ·{" "}
            {date(s.submittedAt)}
          </p>
        </div>
        <span className="badge">{s.status}</span>
      </div>
      {s.status === "GRADED" ? (
        <GradedResult id={s.id} />
      ) : (
        <section className="live-card">
          <h2>
            {s.status === "QUEUED"
              ? "Waiting for grading"
              : s.status === "RUNNING"
                ? "Grading in progress"
                : "Grading did not complete"}
          </h2>
          <p role="status">
            {s.status === "QUEUED"
              ? "Your upload is saved. It will be processed when a grading worker is available."
              : s.status === "RUNNING"
                ? "This page checks for results every five seconds."
                : s.failureReason ||
                  "Contact your instructor before submitting another attempt."}
          </p>
          <button
            className="studio-button secondary"
            onClick={submission.reload}
          >
            Refresh status
          </button>
        </section>
      )}
    </>
  );
}
function GradedResult({ id }: { id: number }) {
  const { user } = useWorkspace();
  const result = useResource<Result>(`/submissions/${id}/result`, true);
  const [tab, setTab] = useState<(typeof tabs)[number]>("Tests");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [link, setLink] = useState<{ url: string; label: string } | null>(null);
  const refs = useRef<Array<HTMLButtonElement | null>>([]);
  function keyboard(event: KeyboardEvent, index: number) {
    if (!["ArrowRight", "ArrowLeft", "Home", "End"].includes(event.key)) return;
    event.preventDefault();
    const next =
      event.key === "Home"
        ? 0
        : event.key === "End"
          ? tabs.length - 1
          : (index + (event.key === "ArrowRight" ? 1 : -1) + tabs.length) %
            tabs.length;
    setTab(tabs[next]);
    refs.current[next]?.focus();
  }
  async function artifact(path: string, label: string) {
    setError("");
    setLink(null);
    setBusy(true);
    try {
      const data = await api.request<{ url: string }>(
        `/submissions/${id}/${path}`,
      );
      const url = new URL(data.url);
      if (!["http:", "https:"].includes(url.protocol))
        throw new Error("Invalid download URL.");
      setLink({ url: url.href, label });
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  if (!result.data)
    return <LoadState error={result.error} retry={result.reload} />;
  const r = result.data;
  return (
    <>
      <div className="review-summary">
        <span>
          <b>{r.effectiveScore}/100</b> recorded score
        </span>
        <span>
          <b>
            {r.testsPassed}/{r.testsTotal}
          </b>{" "}
          tests passed
        </span>
        <span>
          <b>{r.coverageLinePct ?? "—"}%</b> line coverage
        </span>
        <span>Build {r.buildSuccess ? "passed" : "failed"}</span>
      </div>
      <p className="notice">
        Automated score: {r.score}/100.{" "}
        {r.overrides.length > 0
          ? "An instructor has adjusted the recorded score."
          : "No instructor override."}
      </p>
      <div className="split-review">
        <section className="source-pane live-artifacts">
          <div className="pane-title">
            <strong>Submission files</strong>
            <span>PROJECT ZIP</span>
          </div>
          <div className="live-artifact-body">
            <h2>Review the evidence</h2>
            <p>
              Download the submitted archive or inspect the stored build log.
            </p>
            <button
              className="studio-button secondary"
              disabled={busy}
              onClick={() => void artifact("download", "Download project ZIP")}
            >
              Prepare project download
            </button>
            <button
              className="studio-button secondary"
              disabled={busy}
              onClick={() => void artifact("build-log", "Open build log")}
            >
              Prepare build log
            </button>
            {link && (
              <p>
                <a
                  className="text-button"
                  href={link.url}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  {link.label} ↗
                </a>
                <small> Link expires after five minutes.</small>
              </p>
            )}
            <p className="notice">
              Source browsing is not available yet. Only stored artifacts are
              shown here.
            </p>
            {error && (
              <p role="alert" className="live-alert">
                {error}
              </p>
            )}
          </div>
        </section>
        <section className="feedback-pane" aria-label="Review findings">
          <div
            className="workbench-tabs"
            role="tablist"
            aria-label="Review sections"
          >
            {tabs.map((t, index) => (
              <button
                key={t}
                role="tab"
                ref={(node) => {
                  refs.current[index] = node;
                }}
                id={`live-tab-${index}`}
                aria-controls={`live-panel-${index}`}
                aria-selected={tab === t}
                tabIndex={tab === t ? 0 : -1}
                onClick={() => setTab(t)}
                onKeyDown={(event) => keyboard(event, index)}
              >
                {t}
              </button>
            ))}
          </div>
          {tabs.map((t, index) => (
            <div
              key={t}
              role="tabpanel"
              className="review-tab-content"
              id={`live-panel-${index}`}
              aria-labelledby={`live-tab-${index}`}
              hidden={tab !== t}
              tabIndex={0}
            >
              {t === "Tests" && (
                <>
                  <h2>Test results</h2>
                  <p>
                    {r.testsFailed} failed · {r.testsFlaky} flaky · Branch
                    coverage {r.coverageBranchPct ?? "—"}%
                  </p>
                  {r.testCaseResults.length === 0 ? (
                    <p>No test reports were recorded.</p>
                  ) : (
                    r.testCaseResults.map((test, i) => (
                      <details className="live-test" key={i}>
                        <summary>
                          {test.testName}{" "}
                          <span className="badge">{test.status}</span>
                        </summary>
                        <p>{test.durationMs} ms</p>
                        {test.errorMessage && (
                          <p className="preserve-text">{test.errorMessage}</p>
                        )}
                        {test.stackTrace && <pre>{test.stackTrace}</pre>}
                      </details>
                    ))
                  )}
                </>
              )}
              {t === "Security" && (
                <>
                  <h2>Security findings</h2>
                  <p role="status">Scan status: {r.securityStatus}</p>
                  {r.securityStatus !== "COMPLETE" && (
                    <p className="notice">
                      Security checks are not complete. An empty list does not
                      mean the project is clear.
                    </p>
                  )}
                  {r.securityStatus === "COMPLETE" &&
                    r.securityFindings.length === 0 && (
                      <p>No findings reported by the configured checks.</p>
                    )}
                  {r.securityFindings.map((finding, i) => (
                    <article className="failure-detail" key={i}>
                      <span className="failure-label">
                        {finding.severity} · {finding.cweId ?? finding.source}
                      </span>
                      <h3>{finding.ruleId}</h3>
                      <p className="preserve-text">{finding.message}</p>
                      <p>
                        {finding.filePath}
                        {finding.lineNumber ? `:${finding.lineNumber}` : ""}
                      </p>
                      {finding.fixGuidance && <p>{finding.fixGuidance}</p>}
                    </article>
                  ))}
                </>
              )}
              {t === "Guidance" && (
                <>
                  <h2>Next steps</h2>
                  {r.hints.length ? (
                    r.hints.map((hint, i) => (
                      <article key={i}>
                        <p className="preserve-text">{hint.content}</p>
                        <small>
                          Generated with {hint.model}. Review advice before
                          applying it.
                        </small>
                      </article>
                    ))
                  ) : (
                    <>
                      <p>
                        Request a short explanation based on your test errors
                        and security findings.
                      </p>
                      <p className="notice">
                        This sends selected errors and finding locations to the
                        configured model provider. The service may be disabled.
                      </p>
                      <button
                        className="studio-button"
                        disabled={busy || r.securityStatus !== "COMPLETE"}
                        onClick={async () => {
                          setBusy(true);
                          setError("");
                          try {
                            await api.request(`/submissions/${id}/hint`, {
                              method: "POST",
                            });
                            result.reload();
                          } catch (error) {
                            setError(message(error));
                          } finally {
                            setBusy(false);
                          }
                        }}
                      >
                        {busy ? "Requesting…" : "Request guidance"}
                      </button>
                    </>
                  )}
                </>
              )}
              {t === "Review history" && (
                <>
                  <h2>Instructor review</h2>
                  {r.overrides.length === 0 ? (
                    <p>No score changes have been saved.</p>
                  ) : (
                    r.overrides.map((o) => (
                      <article className="failure-detail" key={o.id}>
                        <h3>
                          {o.originalScore} → {o.score}
                        </h3>
                        <p className="preserve-text">{o.reason}</p>
                        <small>
                          {o.instructorName} · {date(o.createdAt)}
                        </small>
                      </article>
                    ))
                  )}
                  {user.role !== "STUDENT" && (
                    <ActionForm
                      key={r.overrides[0]?.id ?? "initial"}
                      label="Save score override"
                      success="Score override saved."
                      action={async (data) => {
                        await api.request(
                          `/submissions/${id}/overrides`,
                          post({
                            score: Number(data.get("score")),
                            expectedScore: r.effectiveScore,
                            reason: data.get("reason"),
                          }),
                        );
                        result.reload();
                      }}
                    >
                      <label>
                        Reviewed score
                        <input
                          name="score"
                          type="number"
                          min="0"
                          max="100"
                          step="0.01"
                          defaultValue={r.effectiveScore}
                          required
                        />
                      </label>
                      <label>
                        Reason for change
                        <textarea
                          name="reason"
                          rows={3}
                          required
                          maxLength={2000}
                        />
                      </label>
                      <p className="notice">
                        Students can see this reason. The automated score is
                        retained.
                      </p>
                    </ActionForm>
                  )}
                </>
              )}
            </div>
          ))}
        </section>
      </div>
    </>
  );
}
