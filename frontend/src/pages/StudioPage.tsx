import { useState } from "react";
import { Link } from "react-router-dom";
import { reviews } from "../data/reviews";

export function StudioPage() {
  const [filter, setFilter] = useState<"All submissions" | "Needs attention">(
    "All submissions",
  );
  const visible = reviews.filter(
    (r) => filter === "All submissions" || r.status === "Needs attention",
  );
  return (
    <>
      <div className="studio-page-heading">
        <div>
          <p className="studio-kicker">SDET · BATCH 16</p>
          <h1>Cohort overview</h1>
          <p>
            Review submissions, track results, and plan your next assignment.
          </p>
        </div>
        <span className="studio-date">Saturday, October 3</span>
      </div>
      <section className="course-banner" aria-label="Current course">
        <div>
          <span className="course-code">SDET / 16</span>
          <h2>Java test automation</h2>
          <p>Selenium · TestNG · Cucumber · Maven</p>
          <div className="course-meta">
            <span>2 students</span>
            <span>3 assignments</span>
            <span>Week 1 of 26</span>
          </div>
        </div>
        <div className="course-next">
          <span>UP NEXT</span>
          <strong>Selenium Login Test</strong>
          <p>Due October 8</p>
          <Link to="/demo/assignments">View student assignments</Link>
        </div>
      </section>
      <div className="studio-metrics">
        <div>
          <span>Awaiting review</span>
          <strong>
            2 <small>submissions</small>
          </strong>
        </div>
        <div>
          <span>Average score</span>
          <strong>
            79 <small>/ 100</small>
          </strong>
        </div>
        <div>
          <span>Average coverage</span>
          <strong>
            72.5<small>%</small>
          </strong>
        </div>
        <div>
          <span>Needs attention</span>
          <strong>
            1 <small>student</small>
          </strong>
        </div>
      </div>
      <div className="studio-grid">
        <section className="studio-review-list">
          <div className="studio-section-title">
            <div>
              <p className="studio-kicker">SUBMISSIONS</p>
              <h2>Ready for review</h2>
            </div>
            <span className="studio-pill">2 submissions</span>
          </div>
          <div className="studio-filter" aria-label="Review filter">
            {(["All submissions", "Needs attention"] as const).map((f) => (
              <button
                key={f}
                aria-pressed={filter === f}
                className={filter === f ? "selected" : ""}
                onClick={() => setFilter(f)}
              >
                {f}
              </button>
            ))}
          </div>
          <div>
            {visible.map((r) => (
              <article className="studio-review-row" key={r.id}>
                <div
                  className={`student-avatar ${r.initials === "MP" ? "violet" : ""}`}
                >
                  {r.initials}
                </div>
                <div className="review-student">
                  <h3>{r.name}</h3>
                  <p>
                    Java Fundamentals <span>· Attempt {r.attempt}</span>
                  </p>
                  <span
                    className={
                      r.status === "Needs attention"
                        ? "attention-text"
                        : "ready-text"
                    }
                  >
                    {r.status}
                  </span>
                </div>
                <div className="row-grade">
                  <strong>
                    {r.score}
                    <small>/100</small>
                  </strong>
                  <span>{r.passed}/10 tests passed</span>
                </div>
                <Link className="studio-button" to={`/demo/review/${r.id}`}>
                  Review<span className="sr-only"> {r.name}</span>
                </Link>
              </article>
            ))}
          </div>
          <p className="studio-table-note">
            Sample submissions · Updated October 2
          </p>
        </section>
      </div>
    </>
  );
}
