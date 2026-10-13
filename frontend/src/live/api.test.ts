import { afterEach, expect, it, vi } from "vitest";
import { ApiClient } from "./api";
const reply = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), {
    status,
    headers: { "Content-Type": "application/json" },
  });
const user = {
  id: 1,
  fullName: "Student",
  email: "s@fixture.local",
  role: "STUDENT",
};
const tokens = { accessToken: "old", refreshToken: "refresh", role: "STUDENT" };
afterEach(() => vi.unstubAllGlobals());
it("shares refresh across concurrent unauthorized requests", async () => {
  let refreshes = 0;
  const fetch = vi.fn(async (url: string, options: RequestInit) => {
    if (url.endsWith("/login")) return reply(tokens);
    if (url.endsWith("/me")) return reply(user);
    if (url.endsWith("/refresh")) {
      refreshes++;
      await new Promise((resolve) => setTimeout(resolve, 10));
      return reply({ ...tokens, accessToken: "new" });
    }
    return new Headers(options.headers).get("Authorization") === "Bearer old"
      ? reply({}, 401)
      : reply({ ok: true });
  });
  vi.stubGlobal("fetch", fetch);
  const api = new ApiClient();
  await api.login("s", "secret");
  expect(await Promise.all([api.request("/one"), api.request("/two")])).toEqual(
    [{ ok: true }, { ok: true }],
  );
  expect(refreshes).toBe(1);
  expect(localStorage.length).toBe(0);
  expect(sessionStorage.length).toBe(0);
});
it("does not resurrect a session when refresh finishes after logout", async () => {
  let release!: (response: Response) => void;
  let started!: () => void;
  const refreshing = new Promise<void>((resolve) => {
    started = resolve;
  });
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) => {
      if (url.endsWith("/login")) return reply(tokens);
      if (url.endsWith("/me")) return reply(user);
      if (url.endsWith("/refresh")) {
        started();
        return new Promise<Response>((resolve) => {
          release = resolve;
        });
      }
      if (url.endsWith("/logout")) return new Response(null, { status: 204 });
      return reply({}, 401);
    }),
  );
  const api = new ApiClient();
  await api.login("s", "secret");
  const request = api.request("/one");
  const rejection = expect(request).rejects.toThrow("session ended");
  await refreshing;
  await api.logout();
  release(reply({ ...tokens, accessToken: "new" }));
  await rejection;
  expect(api.snapshot()).toBeNull();
  await expect(api.request("/two")).rejects.toThrow("sign in");
});
it("preserves multipart boundaries and never retries network failures", async () => {
  let uploads = 0;
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, options: RequestInit) => {
      if (url.endsWith("/login")) return reply(tokens);
      if (url.endsWith("/me")) return reply(user);
      uploads++;
      expect(new Headers(options.headers).has("Content-Type")).toBe(false);
      throw new TypeError("offline");
    }),
  );
  const api = new ApiClient();
  await api.login("s", "secret");
  await expect(
    api.request("/upload", { method: "POST", body: new FormData() }),
  ).rejects.toThrow("may still have completed");
  expect(uploads).toBe(1);
});
it("clears the session if refresh fails", async () => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) =>
      url.endsWith("/login")
        ? reply(tokens)
        : url.endsWith("/me")
          ? reply(user)
          : reply({}, 401),
    ),
  );
  const api = new ApiClient();
  await api.login("s", "secret");
  await expect(api.request("/one")).rejects.toThrow();
  expect(api.snapshot()).toBeNull();
});
