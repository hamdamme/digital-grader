import { useEffect, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api, message } from "./api";

type Mode = "signin" | "register" | "forgot" | "reset";

export function AuthPage({ mode = "signin" }: { mode?: Mode }) {
  const [register, setRegister] = useState(mode === "register");
  const [forgot, setForgot] = useState(mode === "forgot");
  const [resetToken] = useState(() => {
    if (mode !== "reset") return "";
    return new URLSearchParams(window.location.hash.split("?")[1] ?? "").get(
      "token",
    );
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    const email = String(values.get("email") ?? "").trim();
    const password = String(values.get("password") ?? "");
    setBusy(true);
    setError("");
    setNotice("");
    try {
      if (mode === "forgot" || forgot) {
        await api.forgotPassword(email);
        setNotice("If this email is registered, a reset link is on its way.");
      } else if (mode === "reset") {
        await api.resetPassword(resetToken ?? "", password);
        setNotice("Your password is updated. Sign in with the new password.");
        setForgot(false);
        setRegister(false);
        window.location.hash = "#/";
      } else if (register) {
        await api.register(
          String(values.get("name")).trim(),
          email,
          password,
        );
        setRegister(false);
        setNotice(
          "Account created. Check your inbox to verify your email address, then sign in.",
        );
      } else await api.login(email, password);
    } catch (error) {
      setError(message(error));
    } finally {
      setBusy(false);
    }
  }
  const title =
    mode === "forgot" || forgot
      ? "Reset your password"
      : mode === "reset"
        ? "Choose a new password"
        : register
          ? "Create a student account"
          : "Sign in";
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
        <h2>{title}</h2>
        <p>
          {mode === "forgot" || forgot
            ? "Enter your account email and a reset link will follow."
            : mode === "reset"
              ? "Use at least 12 characters and no more than 72 UTF-8 bytes."
              : register
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
          {mode !== "reset" && (
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
          )}
          {mode === "reset" || register || (!register && !forgot && mode === "signin") ? (
            <label>
              Password
              <input
                name="password"
                type="password"
                autoComplete={
                  register || mode === "reset" ? "new-password" : "current-password"
                }
                minLength={register || mode === "reset" ? 12 : undefined}
                maxLength={72}
                required
              />
            </label>
          ) : null}
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
            {busy
              ? "Please wait…"
              : mode === "forgot" || forgot
                ? "Send reset link"
                : mode === "reset"
                  ? "Update password"
                  : register
                    ? "Create account"
                    : "Sign in"}
          </button>
        </form>
        {mode === "signin" && !forgot && (
          <button
            className="text-button"
            disabled={busy}
            onClick={() => {
              setForgot(true);
              setError("");
              setNotice("");
            }}
          >
            Forgot your password?
          </button>
        )}
        {mode === "signin" && (
          <button
            className="text-button"
            disabled={busy}
            onClick={() => {
              setRegister((v) => !v);
              setForgot(false);
              setError("");
              setNotice("");
            }}
          >
            {register ? "Back to sign in" : "New student? Create an account"}
          </button>
        )}
        <p className="notice">
          Your session stays in this tab’s memory. Reloading the page signs you
          out.
        </p>
      </section>
    </main>
  );
}

export function VerifyEmailPage() {
  const [params] = useSearchParams();
  const token = params.get("token") ?? "";
  const [state, setState] = useState<"busy" | "done" | "error">(
    token ? "busy" : "error",
  );
  const [error, setError] = useState(
    token ? "" : "This verification link is incomplete.",
  );
  useEffect(() => {
    if (!token) return;
    let active = true;
    api
      .verifyEmail(token)
      .then(() => active && setState("done"))
      .catch((cause) => {
        if (!active) return;
        setError(message(cause));
        setState("error");
      });
    return () => {
      active = false;
    };
  }, [token]);
  return (
    <main className="auth-page">
      <section className="auth-intro">
        <a className="studio-brand" href="#/">
          <span>DG</span>Digital Grader
        </a>
        <h1>Email verification</h1>
      </section>
      <section className="auth-panel">
        {state === "busy" && <p role="status">Verifying your email address…</p>}
        {state === "done" && (
          <>
            <h2>Email verified</h2>
            <p role="status">You can sign in now.</p>
            <Link className="studio-button" to="/">
              Sign in
            </Link>
          </>
        )}
        {state === "error" && (
          <>
            <h2>Verification failed</h2>
            <p role="alert" className="live-alert">
              {error}
            </p>
            <p className="notice">
              The link may have expired. Sign in to request a fresh one from the
              verification banner.
            </p>
            <Link className="studio-button" to="/">
              Back to sign in
            </Link>
          </>
        )}
      </section>
    </main>
  );
}
