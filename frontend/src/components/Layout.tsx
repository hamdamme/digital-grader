import { useEffect, useRef } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";

export function Layout() {
  const location = useLocation();
  const main = useRef<HTMLElement>(null);
  const initialRoute = useRef(true);
  useEffect(() => {
    if (initialRoute.current) {
      initialRoute.current = false;
      return;
    }
    main.current?.focus();
  }, [location.pathname]);
  const page = location.pathname.split("/")[2];
  const labels: Record<string, string> = {
    assignments: "Assignments",
    submit: "Submit project",
    results: "Feedback",
    progress: "My progress",
  };
  return (
    <>
      <a
        className="skip"
        href="#main"
        onClick={(event) => {
          event.preventDefault();
          main.current?.focus();
          main.current?.scrollIntoView();
        }}
      >
        Skip to content
      </a>
      <aside className="sidebar">
        <Link className="brand" to="/demo/assignments">
          <span className="brand-icon">DG</span>Digital Grader
        </Link>
        <Link
          className="back"
          style={{ margin: "24px 12px 0", color: "#afc8ff" }}
          to="/demo/studio"
        >
          ‹ Teacher studio
        </Link>
        <div className="workspace-label">STUDENT WORKSPACE</div>
        <nav aria-label="Main navigation">
          <NavLink
            to="/demo/assignments"
            className={({ isActive }) =>
              isActive || page === "submit" ? "active" : ""
            }
          >
            <span aria-hidden="true">▦</span>Assignments <small>3</small>
          </NavLink>
          <NavLink to="/demo/progress">
            <span aria-hidden="true">↗</span>My progress
          </NavLink>
          <NavLink to="/demo/results">
            <span aria-hidden="true">≡</span>Latest feedback
          </NavLink>
        </nav>
        <div className="cohort">
          <span className="workspace-label">YOUR COHORT</span>
          <strong>SDET–Batch 16</strong>
          <span>Oct 2026 – Mar 2027</span>
          <div className="cohort-track">
            <span />
          </div>
          <span>Week 1 of 26</span>
        </div>
        <div className="profile">
          <div className="avatar">AL</div>
          <div>
            <strong>Alex Lee</strong>
            <span>Student · Demo account</span>
          </div>
        </div>
      </aside>
      <div className="shell">
        <header className="topbar">
          <span>
            Workspace <span className="slash">/</span>
            <span id="crumb">{labels[page] ?? "Not found"}</span>
          </span>
          <span className="demo-tag">PROTOTYPE · SAMPLE DATA</span>
        </header>
        <main id="main" tabIndex={-1} ref={main}>
          <Outlet />
        </main>
        <footer>
          Digital Grader · Student workspace
          <span>Local prototype · No code is executed</span>
        </footer>
      </div>
    </>
  );
}
