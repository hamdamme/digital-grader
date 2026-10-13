import { useResource, LoadState } from "./hooks";
import type { ReviewRow } from "./types";

type Point = { label: string; value: number | null };
function Trend({
  title,
  points,
  maximum = 100,
}: {
  title: string;
  points: Point[];
  maximum?: number;
}) {
  const scale = Math.max(1, maximum);
  const xy = (point: Point, index: number) =>
    `${25 + index * (270 / Math.max(1, points.length - 1))},${125 - (Number(point.value) / scale) * 100}`;
  const path = points
    .map((point, i) =>
      point.value === null
        ? ""
        : `${i === 0 || points[i - 1].value === null ? "M" : "L"}${xy(point, i)}`,
    )
    .join(" ");
  return (
    <section className="live-card">
      <h2>{title}</h2>
      {points.every((p) => p.value === null) ? (
        <p>No completed measurements yet.</p>
      ) : (
        <svg
          viewBox="0 0 320 160"
          role="img"
          aria-label={`${title} by submission, oldest to newest`}
        >
          <path d="M25 20 V125 H300" fill="none" stroke="#d4ddea" />
          <text x="2" y="25" fontSize="9" fill="#627084">
            {scale}
          </text>
          <text x="8" y="130" fontSize="9" fill="#627084">
            0
          </text>
          <path d={path} fill="none" stroke="#245bdb" strokeWidth="2" />
          {points.map((p, i) =>
            p.value === null ? null : (
              <circle
                key={i}
                cx={25 + i * (270 / Math.max(1, points.length - 1))}
                cy={125 - (Number(p.value) / scale) * 100}
                r="4"
                fill="#245bdb"
              >
                <title>
                  {p.label}: {p.value}
                </title>
              </circle>
            ),
          )}
          <text x="25" y="150" fontSize="10" fill="#627084">
            Oldest
          </text>
          <text x="270" y="150" fontSize="10" fill="#627084">
            Latest
          </text>
        </svg>
      )}
      <details>
        <summary>View values</summary>
        <ul>
          {points.map((p, i) => (
            <li key={i}>
              {p.label}: {p.value ?? "Pending"}
            </li>
          ))}
        </ul>
      </details>
    </section>
  );
}
export function StudentTrends({ rows }: { rows: ReviewRow[] }) {
  const ordered = [...rows].sort(
    (a, b) =>
      Date.parse(a.submittedAt) - Date.parse(b.submittedAt) || a.id - b.id,
  );
  const points = (field: "score" | "coverageLinePct" | "findingCount") =>
    ordered.map((row) => ({
      label: `Submission #${row.id}`,
      value: row[field] ?? null,
    }));
  return (
    <>
      <p className="notice">
        One point per submission, oldest to newest. Missing grades and
        unfinished scans remain gaps.
      </p>
      <div className="live-trends">
        <Trend title="Score over submissions" points={points("score")} />
        <Trend title="Line coverage (%)" points={points("coverageLinePct")} />
        <Trend
          title="Security finding count"
          points={points("findingCount")}
          maximum={Math.max(1, ...ordered.map((r) => r.findingCount ?? 0))}
        />
      </div>
    </>
  );
}
type Summary = {
  studentId: number;
  fullName: string;
  totalSubmissions: number;
  avgScore: number | null;
  avgCoverage: number | null;
  completedScans: number;
  totalFindings: number | null;
};
export function CohortDashboard({ cohortId }: { cohortId: number }) {
  const dashboard = useResource<Summary[]>(
    `/cohorts/${cohortId}/dashboard`,
    true,
  );
  const heatmap = useResource<{ cweId: string; count: number }[]>(
    `/cohorts/${cohortId}/vulnerability-heatmap`,
    true,
  );
  const number = (n: number | null) =>
    n === null ? "—" : Number(n).toFixed(1);
  return (
    <>
      <section className="live-card">
        <h2>Student progress</h2>
        <p className="notice">
          Averages include all scored attempts. Findings include completed scans
          only.
        </p>
        {!dashboard.data ? (
          <LoadState error={dashboard.error} retry={dashboard.reload} />
        ) : dashboard.data.length === 0 ? (
          <p>No enrolled students yet.</p>
        ) : (
          <div className="live-table-wrap">
            <table className="live-table">
              <thead>
                <tr>
                  <th>Student</th>
                  <th>Attempts</th>
                  <th>Average score</th>
                  <th>Average coverage</th>
                  <th>Completed scans</th>
                  <th>Findings</th>
                </tr>
              </thead>
              <tbody>
                {dashboard.data.map((row) => (
                  <tr key={row.studentId}>
                    <td>{row.fullName}</td>
                    <td>{row.totalSubmissions}</td>
                    <td>{number(row.avgScore)}</td>
                    <td>{number(row.avgCoverage)}%</td>
                    <td>{row.completedScans}</td>
                    <td>{row.totalFindings ?? "Pending"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <section className="live-card">
        <h2>Findings by CWE</h2>
        {!heatmap.data ? (
          <LoadState error={heatmap.error} retry={heatmap.reload} />
        ) : heatmap.data.length === 0 ? (
          <p>
            No findings from completed scans. Pending scans are not included.
          </p>
        ) : (
          <ul className="cwe-bars">
            {heatmap.data.map((row) => (
              <li key={row.cweId}>
                <span>{row.cweId}</span>
                <meter
                  min="0"
                  max={Math.max(1, ...heatmap.data!.map((r) => r.count))}
                  value={row.count}
                  aria-label={`${row.cweId} finding count`}
                />
                <strong>{row.count}</strong>
              </li>
            ))}
          </ul>
        )}
        <p className="notice">
          Counts include every completed attempt. Tracking whether individual
          findings were fixed remains planned.
        </p>
      </section>
    </>
  );
}
