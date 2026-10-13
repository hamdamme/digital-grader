import { useRef, useState, type KeyboardEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { codeFiles, reviews } from "../data/reviews";
import { testCases } from "../data/demo";

const tabs = ["Tests", "Security", "Guidance"] as const;
type Tab = (typeof tabs)[number];
type FileName = keyof typeof codeFiles;
export function ReviewPage() {
  const { id } = useParams();
  const review = reviews.find((r) => r.id === id);
  const [file, setFile] = useState<FileName>("CollectionUtils.java");
  const [tab, setTab] = useState<Tab>("Tests");
  const [note, setNote] = useState("");
  const [saved, setSaved] = useState(false);
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([]);
  function selectTab(event: KeyboardEvent, index: number) {
    if (!["ArrowRight", "ArrowLeft", "Home", "End"].includes(event.key)) return;
    event.preventDefault();
    const next =
      event.key === "Home"
        ? 0
        : event.key === "End"
          ? 2
          : (index + (event.key === "ArrowRight" ? 1 : -1) + 3) % 3;
    setTab(tabs[next]);
    tabRefs.current[next]?.focus();
  }
  if (!review)
    return (
      <>
        <h1>Submission not found</h1>
        <Link to="/demo/studio">Return to overview</Link>
      </>
    );
  return (
    <>
      <Link className="studio-back" to="/demo/studio">
        ‹ Back to overview
      </Link>
      <div className="studio-page-heading review-page-heading">
        <div>
          <p className="studio-kicker">
            SUBMISSION / {review.id.toUpperCase()}
          </p>
          <h1>Java Fundamentals</h1>
          <p>
            <strong>{review.name}</strong> · Attempt {review.attempt} ·{" "}
            {review.submitted}
          </p>
        </div>
        <div className="workbench-score">
          <strong>{review.score}</strong>
          <span>
            / 100<small>Automated sample score</small>
          </span>
        </div>
      </div>
      <div className="review-summary">
        <span>
          <b>{review.passed}/10</b> tests passed
        </span>
        <span>
          <b>{review.coverage}%</b> line coverage
        </span>
        <span className="attention-text">
          <b>1 critical</b> security finding
        </span>
        <span className="fixture-label">Illustrative source files</span>
      </div>
      <div className="split-review">
        <section className="source-pane" aria-label="Submitted code">
          <div className="pane-title">
            <strong>Project files</strong>
            <span>JAVA 17</span>
          </div>
          <div className="file-selector" aria-label="Choose source file">
            {(Object.keys(codeFiles) as FileName[]).map((name) => (
              <button
                key={name}
                onClick={() => setFile(name)}
                aria-pressed={file === name}
                className={file === name ? "selected" : ""}
              >
                <span aria-hidden="true">▤</span>
                {name}
              </button>
            ))}
          </div>
          <div className="source-path">
            src/{file === "CollectionsTest.java" ? "test" : "main"}
            /java/com/example/{file}
          </div>
          <div className="code-view" tabIndex={0} aria-label={file}>
            <pre>
              {codeFiles[file].map((line, i) => (
                <div
                  className={`code-line ${(file === "CollectionUtils.java" && i === 7) || (file === "Config.java" && i === 4) ? "line-issue" : ""}`}
                  key={i}
                >
                  <span className="line-number" aria-hidden="true">
                    {i + 1}
                  </span>
                  <code>{line || " "}</code>
                </div>
              ))}
            </pre>
          </div>
          <div className="source-footnote">
            <span className="issue-marker" />
            Highlighted line relates to a sample finding. Code is read-only.
          </div>
        </section>
        <section className="feedback-pane" aria-label="Review findings">
          <div
            className="workbench-tabs"
            role="tablist"
            aria-label="Review sections"
          >
            {tabs.map((t, i) => (
              <button
                key={t}
                ref={(node) => {
                  tabRefs.current[i] = node;
                }}
                role="tab"
                id={`review-tab-${t}`}
                aria-controls={`review-panel-${t}`}
                aria-selected={tab === t}
                tabIndex={tab === t ? 0 : -1}
                onClick={() => setTab(t)}
                onKeyDown={(e) => selectTab(e, i)}
              >
                {t}
                {t === "Security" && <small>1</small>}
              </button>
            ))}
          </div>
          {tabs.map((t) => (
            <div
              className="review-tab-content"
              key={t}
              role="tabpanel"
              id={`review-panel-${t}`}
              aria-labelledby={`review-tab-${t}`}
              hidden={tab !== t}
              tabIndex={0}
            >
              {t === "Tests" && (
                <>
                  <div className="test-summary">
                    <span className="failure-dot" />
                    <strong>
                      {10 - review.passed}{" "}
                      {review.passed === 9 ? "test needs" : "tests need"}{" "}
                      attention
                    </strong>
                    <span>{review.passed} passed</span>
                  </div>
                  <article className="failure-detail">
                    <span className="failure-label">FAILED</span>
                    <h2>nullInputRejected</h2>
                    <p>
                      The method throws a different exception than the test
                      expects.
                    </p>
                    <dl>
                      <div>
                        <dt>Expected</dt>
                        <dd>IllegalArgumentException</dd>
                      </div>
                      <div>
                        <dt>Received</dt>
                        <dd>NullPointerException</dd>
                      </div>
                    </dl>
                    <button
                      className="text-button"
                      onClick={() => setFile("CollectionUtils.java")}
                    >
                      Show related source · line 8
                    </button>
                  </article>
                  <details className="all-tests">
                    <summary>View all 10 test results</summary>
                    {testCases.map((test, index) => (
                      <div key={test.name}>
                        <code>{test.name}</code>
                        <span
                          className={
                            index < review.passed
                              ? "ready-text"
                              : "attention-text"
                          }
                        >
                          {index < review.passed ? "Passed" : "Failed"}
                        </span>
                      </div>
                    ))}
                  </details>
                </>
              )}
              {t === "Security" && (
                <article className="failure-detail">
                  <span className="failure-label">CRITICAL · CWE-798</span>
                  <h2>Hardcoded credential</h2>
                  <p>
                    The sample configuration stores a password in source code.
                    Read credentials from environment variables and keep them
                    out of version control.
                  </p>
                  <button
                    className="text-button"
                    onClick={() => setFile("Config.java")}
                  >
                    Show related source · line 5
                  </button>
                  <p className="notice">
                    The displayed value is an illustrative fixture, not a real
                    credential.
                  </p>
                </article>
              )}
              {t === "Guidance" && (
                <div className="guidance-content">
                  <span className="studio-kicker">
                    HELP THE STUDENT FIND THE NEXT STEP
                  </span>
                  <h2>Start with the order of operations.</h2>
                  <p>
                    The collection is accessed before the input is validated.
                  </p>
                  <ol>
                    <li>
                      Ask the student to compare the expected and actual
                      exceptions.
                    </li>
                    <li>
                      Point them toward the first operation performed on the
                      collection.
                    </li>
                    <li>
                      Encourage a focused null-input test before changing the
                      method.
                    </li>
                  </ol>
                  <p className="notice">
                    Sample teaching guidance · No AI service connected
                  </p>
                </div>
              )}
            </div>
          ))}
          <form
            className="review-note"
            onSubmit={(event) => {
              event.preventDefault();
              setSaved(true);
            }}
          >
            <label htmlFor="teacher-note">Your review note</label>
            <textarea
              id="teacher-note"
              rows={3}
              placeholder="What should the student focus on next?"
              value={note}
              onChange={(e) => {
                setNote(e.target.value);
                setSaved(false);
              }}
              required
            />
            <div>
              <span role="status">
                {saved
                  ? "Draft kept for this view. Nothing sent."
                  : "Local draft only · Never sent to a student"}
              </span>
              <button
                className="studio-button"
                type="submit"
                disabled={!note.trim()}
              >
                Keep draft
              </button>
            </div>
          </form>
        </section>
      </div>
    </>
  );
}
