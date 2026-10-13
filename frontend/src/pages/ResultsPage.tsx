import { useRef, useState, type KeyboardEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { Badge, Heading } from "../components/UI";
import { testCases } from "../data/demo";

const tabs = ["Tests", "Security", "Hints"] as const;
type Tab = (typeof tabs)[number];
export function ResultsPage() {
  const [params] = useSearchParams();
  const [tab, setTab] = useState<Tab>("Tests");
  const [hintVisible, setHintVisible] = useState(false);
  const buttons = useRef<Array<HTMLButtonElement | null>>([]);
  function onTabKey(event: KeyboardEvent, index: number) {
    if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
    event.preventDefault();
    const next =
      event.key === "Home"
        ? 0
        : event.key === "End"
          ? tabs.length - 1
          : (index + (event.key === "ArrowRight" ? 1 : -1) + tabs.length) %
            tabs.length;
    setTab(tabs[next]);
    buttons.current[next]?.focus();
  }
  const simulation = params.get("simulation") === "1";
  return (
    <>
      <Link className="back" to="/demo/assignments">
        ‹ All assignments
      </Link>
      <Heading
        eyebrow="SUBMISSION / DEMO-003"
        title={simulation ? "Simulation report" : "Submission report"}
        detail={
          simulation
            ? "Simulation complete · Showing the Java Fundamentals sample report"
            : "Java Fundamentals · Demonstration report · Oct 2, 2026"
        }
      />
      <div className="panel score">
        <div className="score-ring">86</div>
        <div>
          <h2>Java Fundamentals</h2>
          <p className="muted">
            9 of 10 tests passed · 80% line coverage · 1 security finding
          </p>
          <Badge>Sample graded result</Badge>
        </div>
      </div>
      <section className="panel">
        <div className="tabs" role="tablist" aria-label="Feedback sections">
          {tabs.map((t, index) => (
            <button
              type="button"
              key={t}
              ref={(node) => {
                buttons.current[index] = node;
              }}
              role="tab"
              id={`tab-${t}`}
              aria-controls={`panel-${t}`}
              aria-selected={tab === t}
              tabIndex={tab === t ? 0 : -1}
              className={tab === t ? "selected" : ""}
              onClick={() => setTab(t)}
              onKeyDown={(event) => onTabKey(event, index)}
            >
              {t === "Security" ? "Security (1)" : t}
            </button>
          ))}
        </div>
        {tabs.map((t) => (
          <div
            key={t}
            id={`panel-${t}`}
            role="tabpanel"
            aria-labelledby={`tab-${t}`}
            hidden={tab !== t}
            tabIndex={0}
          >
            {t === "Tests" && (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th scope="col">Test case</th>
                      <th scope="col">Result</th>
                      <th scope="col">Duration</th>
                    </tr>
                  </thead>
                  <tbody>
                    {testCases.map((test) => (
                      <tr key={test.name}>
                        <td>
                          <code>{test.name}</code>
                          {!test.passed && (
                            <details>
                              <summary>See failure</summary>
                              <pre>
                                {
                                  "AssertionError: expected IllegalArgumentException\nActual: NullPointerException\n at CollectionsTest.java:42"
                                }
                              </pre>
                            </details>
                          )}
                        </td>
                        <td>
                          <Badge tone={test.passed ? "" : "amber"}>
                            {test.passed ? "Passed" : "Failed"}
                          </Badge>
                        </td>
                        <td>{test.durationMs} ms</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {t === "Security" && (
              <article className="finding">
                <Badge>CRITICAL · CWE-798</Badge>
                <h3>Hardcoded credential</h3>
                <code>src/test/java/Config.java:12</code>
                <p>
                  A password is stored directly in the source. Read sensitive
                  configuration from an environment variable and keep local
                  secrets out of Git.
                </p>
                <a
                  className="button secondary"
                  href="https://cwe.mitre.org/data/definitions/798.html"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Read about CWE-798
                </a>
              </article>
            )}
            {t === "Hints" && (
              <>
                <h2>A nudge in the right direction</h2>
                <p className="muted">
                  Use guidance to work through the issue yourself.
                </p>
                {!hintVisible && (
                  <button
                    className="button"
                    onClick={() => setHintVisible(true)}
                  >
                    Show sample hint
                  </button>
                )}
                <div aria-live="polite">
                  {hintVisible && (
                    <>
                      <p>
                        Your method accesses the input before checking whether
                        it is null.
                      </p>
                      <ol>
                        <li>Check which exception the test expects.</li>
                        <li>
                          Review the order of validation and collection access.
                        </li>
                        <li>
                          Add an explicit null-input test before changing the
                          implementation.
                        </li>
                      </ol>
                      <p className="notice">
                        Written sample guidance. No AI service is connected.
                      </p>
                    </>
                  )}
                </div>
              </>
            )}
          </div>
        ))}
      </section>
    </>
  );
}
