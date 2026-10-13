import { StudentTrends, CohortDashboard } from "./Dashboard";
import { Link } from "react-router-dom";
import { useWorkspace } from "./Workspace";
import { LoadState, useResource } from "./hooks";
import type { ReviewRow } from "./types";
export const date = (value: string) => new Date(value).toLocaleString();
export function NoCohort() {
  const { user } = useWorkspace();
  return (
    <section className="live-card">
      <h1>No cohorts yet</h1>
      <p>
        {user.role === "STUDENT"
          ? `Ask your instructor to enroll account #${user.id}.`
          : "Create your first cohort to add students and assignments."}
      </p>
      {user.role !== "STUDENT" && (
        <Link className="studio-button" to="/cohorts">
          Manage cohorts
        </Link>
      )}
    </section>
  );
}
export function Overview({
  progress = false,
  listOnly = false,
}: {
  progress?: boolean;
  listOnly?: boolean;
}) {
  const { cohort, user } = useWorkspace();
  const rows = useResource<ReviewRow[]>(
    cohort ? `/submissions?cohortId=${cohort.id}` : null,
    true,
  );
  if (!cohort) return <NoCohort />;
  if (!rows.data) return <LoadState error={rows.error} retry={rows.reload} />;
  const graded = rows.data.filter((r) => r.score !== null);
  const average = graded.length
    ? (
        graded.reduce((sum, r) => sum + Number(r.score), 0) / graded.length
      ).toFixed(1)
    : "—";
  return (
    <>
      <div className="studio-page-heading">
        <div>
          <p className="studio-kicker">{cohort.name}</p>
          <h1>
            {progress
              ? "My progress"
              : listOnly
                ? "Submissions"
                : "Cohort overview"}
          </h1>
          <p>Track submissions and open each result for details.</p>
        </div>
        <button className="studio-button secondary" onClick={rows.reload}>
          Refresh
        </button>
      </div>
      {!listOnly && (
        <>
          <section className="course-banner">
            <div>
              <span className="course-code">
                {user.role === "STUDENT" ? "YOUR LEARNING" : "YOUR COHORT"}
              </span>
              <h2>{cohort.name}</h2>
              <p>
                {cohort.startDate} — {cohort.endDate}
              </p>
              <div className="course-meta">
                <span>
                  {cohort.studentCount}{" "}
                  {cohort.studentCount === 1 ? "student" : "students"}
                </span>
              </div>
            </div>
            <Link className="studio-button" to="/assignments">
              View assignments
            </Link>
          </section>
          <div className="studio-metrics">
            <div>
              <span>Submissions</span>
              <strong>{rows.data.length}</strong>
            </div>
            <div>
              <span>Queued or running</span>
              <strong>
                {
                  rows.data.filter((r) =>
                    ["QUEUED", "RUNNING"].includes(r.status),
                  ).length
                }
              </strong>
            </div>
            <div>
              <span>Average recorded score</span>
              <strong>
                {average}
                <small> / 100</small>
              </strong>
            </div>
            <div>
              <span>Failed or timed out</span>
              <strong>
                {
                  rows.data.filter((r) =>
                    ["FAILED", "TIMEOUT"].includes(r.status),
                  ).length
                }
              </strong>
            </div>
          </div>
          <p className="notice">
            Average includes every scored attempt and any instructor overrides.
          </p>
        </>
      )}
      {progress && <StudentTrends rows={rows.data} />}
      {!listOnly && user.role !== "STUDENT" && (
        <CohortDashboard cohortId={cohort.id} />
      )}
      <section className="live-card">
        <h2>{progress ? "Attempt history" : "Recent submissions"}</h2>
        {rows.data.length === 0 ? (
          <p>No submissions yet. Uploaded projects will appear here.</p>
        ) : (
          <div className="live-table-wrap">
            <table className="live-table">
              <thead>
                <tr>
                  <th>Assignment</th>
                  {user.role !== "STUDENT" && <th>Student</th>}
                  <th>Attempt</th>
                  <th>Status</th>
                  <th>Score</th>
                  <th>Submitted</th>
                  <th>Review</th>
                </tr>
              </thead>
              <tbody>
                {rows.data.map((row) => (
                  <tr key={row.id}>
                    <td>{row.assignmentTitle}</td>
                    {user.role !== "STUDENT" && <td>{row.studentName}</td>}
                    <td>{row.attemptNumber}</td>
                    <td>
                      <span className="badge">{row.status}</span>
                    </td>
                    <td>{row.score ?? "—"}</td>
                    <td>{date(row.submittedAt)}</td>
                    <td>
                      <Link className="text-button" to={`/review/${row.id}`}>
                        Open{" "}
                        <span className="sr-only">submission {row.id}</span>
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
