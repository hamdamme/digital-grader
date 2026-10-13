import { useEffect, useState } from "react";
import { api, message } from "./api";
export function useResource<T>(path: string | null, poll = false) {
  const [state, setState] = useState<{
    path: string | null;
    data?: T;
    error?: string;
  }>({ path: null });
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!path) return;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;
    async function load() {
      try {
        const data = await api.request<T>(path!, { signal: controller.signal });
        if (!controller.signal.aborted) setState({ path, data });
      } catch (error) {
        if (!controller.signal.aborted)
          setState({ path, error: message(error) });
      } finally {
        if (poll && !controller.signal.aborted) timer = setTimeout(load, 5000);
      }
    }
    void load();
    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [path, revision, poll]);
  const current: { data?: T; error?: string } =
    state.path === path ? state : {};
  return { ...current, reload: () => setRevision((v) => v + 1) };
}
export function LoadState({
  error,
  retry,
}: {
  error?: string;
  retry: () => void;
}) {
  return error ? (
    <div role="alert" className="live-alert">
      {error}{" "}
      <button type="button" onClick={retry}>
        Try again
      </button>
    </div>
  ) : (
    <p role="status">Loading…</p>
  );
}
