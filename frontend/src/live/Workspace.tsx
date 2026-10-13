import { createContext, useContext, useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet, useLocation } from "react-router-dom";
import { api, message } from "./api";
import { LoadState, useResource } from "./hooks";
import type { Cohort, User } from "./types";
const WorkspaceContext = createContext<{
  user: User;
  cohort?: Cohort;
  reload: () => void;
} | null>(null);
export const useWorkspace = () => useContext(WorkspaceContext)!;
export function Workspace({ user }: { user: User }) {
  const cohorts = useResource<Cohort[]>("/cohorts/mine");
  const [selected, setSelected] = useState("");
  const cohort =
    cohorts.data?.find((c) => String(c.id) === selected) ?? cohorts.data?.[0];
  const teacher = user.role !== "STUDENT";
  const main = useRef<HTMLElement>(null);
  const location = useLocation();
  useEffect(() => {
    main.current?.focus();
  }, [location.pathname]);
  return (
    <WorkspaceContext.Provider value={{ user, cohort, reload: cohorts.reload }}>
      <div className="teacher-shell connected-shell">
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
          <Link className="studio-brand" to="/">
            <span>DG</span>Digital Grader
          </Link>
          <p className="studio-nav-label">
            {teacher ? "INSTRUCTOR" : "STUDENT"} WORKSPACE
          </p>
          <nav aria-label="Workspace navigation">
            {teacher && <NavLink to="/studio">▦ Overview</NavLink>}
            <NavLink to="/assignments">▤ Assignments</NavLink>
            <NavLink to="/results">
              ☷ {teacher ? "Submissions" : "My submissions"}
            </NavLink>
            {!teacher && <NavLink to="/progress">↗ My progress</NavLink>}
            {teacher && <NavLink to="/cohorts">＋ Manage cohort</NavLink>}
          </nav>
          <div className="studio-course-label">
            <label className="studio-nav-label" htmlFor="cohort-select">
              CURRENT COHORT
            </label>
            <select
              id="cohort-select"
              value={cohort?.id ?? ""}
              onChange={(e) => setSelected(e.target.value)}
            >
              <option value="" disabled>
                Choose a cohort
              </option>
              {cohorts.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
            {cohort && (
              <p>
                {cohort.startDate} — {cohort.endDate}
              </p>
            )}
          </div>
          <div className="studio-bottom">
            <div className="teacher-profile">
              <span>{user.fullName.slice(0, 2).toUpperCase()}</span>
              <div>
                <strong>{user.fullName}</strong>
                <small>
                  {user.role.toLowerCase()} · Account #{user.id}
                </small>
              </div>
            </div>
            <button
              className="sidebar-logout"
              onClick={() =>
                void api
                  .logout()
                  .catch((error) =>
                    window.alert(`Signed out locally. ${message(error)}`),
                  )
              }
            >
              Sign out
            </button>
          </div>
        </aside>
        <div className="studio-shell">
          <header className="studio-topbar">
            <span>{cohort?.name ?? "Your workspace"}</span>
            <span className="demo-tag">CONNECTED WORKSPACE</span>
          </header>
          <main id="main" ref={main} tabIndex={-1} className="studio-main">
            {!cohorts.data ? (
              <LoadState error={cohorts.error} retry={cohorts.reload} />
            ) : (
              <Outlet key={cohort?.id ?? "none"} />
            )}
          </main>
          <footer className="studio-footer">
            <span>Digital Grader · Learning and review</span>
            <span>Grading needs a configured worker</span>
          </footer>
        </div>
      </div>
    </WorkspaceContext.Provider>
  );
}
