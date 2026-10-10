import { useEffect, useRef } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";

export function TeacherLayout() {
  const { pathname } = useLocation();
  const main = useRef<HTMLElement>(null);
  useEffect(() => {
    main.current?.focus();
  }, [pathname]);
  return (
    <div className="teacher-shell">
      <a
        className="skip"
        href="#main"
        onClick={(e) => {
          e.preventDefault();
          main.current?.focus();
        }}
      >
        Skip to content
      </a>
      <aside className="studio-sidebar">
        <Link className="studio-brand" to="/studio">
          <span>DG</span>Digital Grader
        </Link>
        <p className="studio-nav-label">INSTRUCTOR WORKSPACE</p>
        <nav aria-label="Teacher navigation">
          <NavLink to="/studio">
            <span aria-hidden="true">▦</span> Overview
          </NavLink>
          <NavLink to="/review/demo-003">
            <span aria-hidden="true">☷</span> Submission review <small>2</small>
          </NavLink>
        </nav>
        <div className="studio-course-label">
          <p className="studio-nav-label">CURRENT COHORT</p>
          <strong>SDET · Batch 16</strong>
          <p>Java test automation</p>
          <span className="studio-pill">Fall 2026</span>
        </div>
        <div className="studio-bottom">
          <Link to="/assignments">Open student preview</Link>
          <div className="teacher-profile">
            <span>IN</span>
            <div>
              <strong>Instructor</strong>
              <small>Demo workspace</small>
            </div>
          </div>
        </div>
      </aside>
      <div className="studio-shell">
        <header className="studio-topbar">
          <span>
            Mindtek <span className="slash">/</span> SDET · Batch 16
          </span>
          <span className="demo-tag">DESIGN PREVIEW · SAMPLE DATA</span>
        </header>
        <main className="studio-main" id="main" ref={main} tabIndex={-1}>
          <Outlet />
        </main>
        <footer className="studio-footer">
          <span>Digital Grader · Independent project for Mindtek</span>
          <span>Demo only · Changes reset on reload</span>
        </footer>
      </div>
    </div>
  );
}
