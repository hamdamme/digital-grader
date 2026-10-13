import { useState } from "react";
import { Link } from "react-router-dom";
import { assignments } from "../data/demo";
import { Badge, Heading, Stat } from "../components/UI";

const filters = ["All assignments", "Active", "Completed"] as const;
export function AssignmentsPage() {
  const [filter, setFilter] =
    useState<(typeof filters)[number]>("All assignments");
  const visible = assignments.filter(
    (a) =>
      filter === "All assignments" ||
      (filter === "Active"
        ? a.status !== "Completed"
        : a.status === "Completed"),
  );
  return (
    <>
      <Heading
        title="Assignments"
        detail="Manage your submissions and review automated feedback."
      />
      <div className="stats">
        <Stat label="Completed" value="1" suffix="/ 3 assignments" />
        <Stat label="Best score" value="86" suffix="/ 100" />
        <Stat
          label="Next deadline"
          value="Oct 8"
          suffix="Selenium Login Test"
        />
      </div>
      <div className="workbench">
        <section className="assignment-section" aria-label="Assignments">
          <div className="section-heading">
            <div className="filters" aria-label="Filter assignments">
              {filters.map((f) => (
                <button
                  key={f}
                  className={f === filter ? "selected" : ""}
                  aria-pressed={f === filter}
                  onClick={() => setFilter(f)}
                >
                  {f}
                </button>
              ))}
            </div>
            <span className="count">
              {visible.length}{" "}
              {visible.length === 1 ? "assignment" : "assignments"}
            </span>
          </div>
          <div className="assignment-list">
            {visible.map((a) => (
              <article className="assignment-row" key={a.id}>
                <div className="assignment-id">A–0{a.id}</div>
                <div className="assignment-body">
                  <div className="row-heading">
                    <h2>{a.title}</h2>
                    <Badge
                      tone={
                        a.status === "In progress"
                          ? "amber"
                          : a.status === "Not started"
                            ? "neutral"
                            : ""
                      }
                    >
                      {a.status}
                    </Badge>
                  </div>
                  <p>{a.description}</p>
                  <div className="tags">
                    {a.tags.map((tag) => (
                      <span key={tag}>{tag}</span>
                    ))}
                  </div>
                  <div className="row-meta">
                    <span>Due {a.due}</span>
                    <span>
                      {a.attempts} of {a.maxAttempts} attempts
                    </span>
                    <Link
                      className={`button ${a.id !== 1 ? "secondary" : ""}`}
                      to={
                        a.status === "Completed"
                          ? "/results"
                          : `/submit/${a.id}`
                      }
                    >
                      {a.status === "Completed"
                        ? "View feedback"
                        : a.status === "In progress"
                          ? "Continue assignment"
                          : "Start assignment"}
                    </Link>
                  </div>
                </div>
              </article>
            ))}
          </div>
        </section>
        <aside className="review-rail">
          <div className="rail-label">
            LATEST REVIEW <span>DEMO-003</span>
          </div>
          <h2>Java Fundamentals</h2>
          <div className="review-score">
            86<span>/ 100</span>
          </div>
          <div className="score-track">
            <span />
          </div>
          <dl>
            <div>
              <dt>Tests passed</dt>
              <dd>9 / 10</dd>
            </div>
            <div>
              <dt>Line coverage</dt>
              <dd>80%</dd>
            </div>
            <div>
              <dt>Security findings</dt>
              <dd className="error">1 critical</dd>
            </div>
          </dl>
          <div className="rail-failure">
            <span className="failure-label">FAILING TEST</span>
            <code>nullInputRejected</code>
            <p>
              Expected IllegalArgumentException; received NullPointerException.
            </p>
          </div>
          <Link className="button secondary" to="/demo/results">
            Open full report
          </Link>
          <p className="notice">Sample feedback · Oct 2, 2026</p>
        </aside>
      </div>
    </>
  );
}
