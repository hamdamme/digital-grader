import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { assignments } from "../data/demo";
import { Heading, NotFound } from "../components/UI";
import { validateSubmission } from "../lib/submission";

type Phase = "idle" | "queued" | "running";
export function SubmitPage() {
  const { id } = useParams();
  const assignment = assignments.find((a) => a.id === Number(id));
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [phase, setPhase] = useState<Phase>("idle");
  const submitted = useRef(false);
  const busy = phase !== "idle";
  useEffect(() => {
    if (phase === "idle") return;
    const timer = window.setTimeout(
      () => {
        if (phase === "queued") setPhase("running");
        else navigate("/demo/results?simulation=1");
      },
      phase === "queued" ? 800 : 1100,
    );
    return () => window.clearTimeout(timer);
  }, [phase, navigate]);
  function submit(event: FormEvent) {
    event.preventDefault();
    if (submitted.current) return;
    const message = validateSubmission(file);
    setError(message);
    if (message) return;
    submitted.current = true;
    setPhase("queued");
  }
  if (!assignment) return <NotFound assignment />;
  return (
    <>
      <Link className="back" to="/demo/assignments">
        ‹ All assignments
      </Link>
      <Heading
        eyebrow={`ASSIGNMENT / 0${assignment.id}`}
        title={assignment.title}
        detail={assignment.description}
      />
      <form className="panel" onSubmit={submit}>
        <h2>Submit your project</h2>
        <p className="muted">
          Package your Maven project as a ZIP, including pom.xml and the src
          folder.
        </p>
        <div className="dropzone">
          <label htmlFor="project">Choose your project ZIP</label>
          <p className="notice" id="file-help">
            ZIP only · Up to 50 MB. File contents are not inspected in this
            demo.
          </p>
          <input
            id="project"
            type="file"
            accept=".zip,application/zip"
            disabled={busy}
            aria-describedby="file-help submission-status"
            aria-invalid={Boolean(error)}
            onChange={(event) => {
              setFile(event.target.files?.[0] ?? null);
              setError(null);
            }}
          />
        </div>
        <p className="notice">
          Demo only: the file stays on your device. This simulates grading with
          sample results.
        </p>
        <button className="button" type="submit" disabled={busy}>
          Simulate submission
        </button>
        <p
          className={`message ${error ? "error" : ""}`}
          id="submission-status"
          role="status"
          aria-live="polite"
        >
          {error ??
            (phase === "queued"
              ? "Queued — preparing sample feedback…"
              : phase === "running"
                ? "Running — simulating test and security checks…"
                : "")}
        </p>
      </form>
    </>
  );
}
