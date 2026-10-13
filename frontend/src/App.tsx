import { useSyncExternalStore } from "react";
import { Link, Navigate, Route, Routes, useParams } from "react-router-dom";
import { DemoApp } from "./DemoApp";
import { api } from "./live/api";
import { AuthPage } from "./live/AuthPage";
import { Workspace } from "./live/Workspace";
import { Overview } from "./live/Overview";
import { AssignmentsPage, CohortsPage } from "./live/ManagePages";
import { SubmitPage } from "./live/SubmitPage";
import { ReviewPage } from "./live/ReviewPage";
function SubmissionRoute({ review = false }: { review?: boolean }) {
  const { id } = useParams();
  return review ? <ReviewPage key={id} /> : <SubmitPage key={id} />;
}
export function App() {
  const user = useSyncExternalStore(api.subscribe, api.snapshot);
  return (
    <Routes>
      <Route path="demo/*" element={<DemoApp />} />
      {!user ? (
        <Route path="*" element={<AuthPage />} />
      ) : (
        <Route element={<Workspace key={user.id} user={user} />}>
          <Route
            index
            element={
              <Navigate
                to={user.role === "STUDENT" ? "/assignments" : "/studio"}
                replace
              />
            }
          />
          <Route
            path="studio"
            element={
              user.role === "STUDENT" ? (
                <Navigate to="/assignments" replace />
              ) : (
                <Overview />
              )
            }
          />
          <Route path="cohorts" element={<CohortsPage />} />
          <Route path="assignments" element={<AssignmentsPage />} />
          <Route path="submit/:id" element={<SubmissionRoute />} />
          <Route path="review/:id" element={<SubmissionRoute review />} />
          <Route path="results" element={<Overview listOnly />} />
          <Route
            path="progress"
            element={
              user.role === "STUDENT" ? (
                <Overview progress />
              ) : (
                <Navigate to="/studio" replace />
              )
            }
          />
          <Route
            path="*"
            element={
              <>
                <h1>Page not found</h1>
                <Link to="/">Return to workspace</Link>
              </>
            }
          />
        </Route>
      )}
    </Routes>
  );
}
