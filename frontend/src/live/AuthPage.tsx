import { useState, type FormEvent } from "react";
import { api, message } from "./api";
export function AuthPage() {
  const [register, setRegister] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const email = String(values.get("email")).trim();
    const password = String(values.get("password"));
    setBusy(true);
    setError("");
    setNotice("");
    try {
      if (register) {
        await api.register(String(values.get("name")).trim(), email, password);
        setRegister(false);
        setNotice("Your student account is ready. Sign in to continue.");
      } else await api.login(email, password);
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="auth-page">
      <section className="auth-intro">
        <a className="studio-brand" href="#/">
          <span>DG</span>Digital Grader
        </a>
        <h1>A clearer view of every submission.</h1>
        <p>One workspace for assignments, results, and thoughtful feedback.</p>
        <a href="#/demo/studio">Explore the sample workspace →</a>
      </section>
      <section className="auth-panel">
        <p className="studio-kicker">WELCOME TO YOUR WORKSPACE</p>
        <h2>{register ? "Create a student account" : "Sign in"}</h2>
        <p>
          {register
            ? "Your instructor will add your account to a cohort."
            : "Continue with your student or instructor account."}
        </p>
        <form className="live-form" onSubmit={submit}>
          {register && (
            <label>
              Full name
              <input name="name" autoComplete="name" required maxLength={255} />
            </label>
          )}
          <label>
            Email
            <input
              name="email"
              type="email"
              autoComplete="username"
              required
              maxLength={255}
            />
          </label>
          <label>
            Password
            <input
              name="password"
              type="password"
              autoComplete={register ? "new-password" : "current-password"}
              minLength={register ? 12 : undefined}
              maxLength={72}
              required
            />
          </label>
          {register && (
            <p className="notice">
              Use at least 12 characters and no more than 72 UTF-8 bytes.
            </p>
          )}
          {error && (
            <p role="alert" className="live-alert">
              {error}
            </p>
          )}
          {notice && <p role="status">{notice}</p>}
          <button className="studio-button" disabled={busy}>
            {busy ? "Please wait…" : register ? "Create account" : "Sign in"}
          </button>
        </form>
        <button
          className="text-button"
          disabled={busy}
          onClick={() => {
            setRegister((v) => !v);
            setError("");
            setNotice("");
          }}
        >
          {register ? "Back to sign in" : "New student? Create an account"}
        </button>
        <p className="notice">
          Your session stays in this tab’s memory. Reloading the page signs you
          out.
        </p>
      </section>
    </main>
  );
}
