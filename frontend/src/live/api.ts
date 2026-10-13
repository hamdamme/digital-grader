import type { Role, User } from "./types";

type Tokens = { accessToken: string; refreshToken: string; role: Role };
export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export class ApiClient {
  private tokens: Tokens | null = null;
  private generation = 0;
  private refresh: Promise<void> | null = null;
  private listeners = new Set<() => void>();
  private user: User | null = null;
  constructor(private base = "/api/v1") {}
  snapshot = () => this.user;
  subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  };
  private publish(user: User | null) {
    this.user = user;
    this.listeners.forEach((listener) => listener());
  }
  clear() {
    this.generation++;
    this.tokens = null;
    this.refresh = null;
    this.publish(null);
  }
  private async send<T>(
    path: string,
    options: RequestInit = {},
    token?: string,
  ): Promise<T> {
    const headers = new Headers(options.headers);
    if (token) headers.set("Authorization", `Bearer ${token}`);
    if (options.body && !(options.body instanceof FormData))
      headers.set("Content-Type", "application/json");
    const controller = new AbortController();
    const abort = () => controller.abort();
    options.signal?.addEventListener("abort", abort, { once: true });
    if (options.signal?.aborted) controller.abort();
    const timeout = setTimeout(
      abort,
      options.body instanceof FormData ? 120000 : 35000,
    );
    try {
      const response = await fetch(`${this.base}${path}`, {
        ...options,
        headers,
        signal: controller.signal,
        credentials: "omit",
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new ApiError(
          response.status,
          typeof body.message === "string"
            ? body.message.slice(0, 300)
            : `Request failed (${response.status})`,
        );
      }
      return response.status === 204
        ? (undefined as T)
        : ((await response.json()) as T);
    } catch (error) {
      if (error instanceof ApiError || options.signal?.aborted) throw error;
      throw new ApiError(
        0,
        "Could not reach the server. A request may still have completed; refresh the view before trying again.",
      );
    } finally {
      clearTimeout(timeout);
      options.signal?.removeEventListener("abort", abort);
    }
  }
  async login(email: string, password: string) {
    this.clear();
    const generation = this.generation;
    const tokens = await this.send<Tokens>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    const user = await this.send<User>("/auth/me", {}, tokens.accessToken);
    if (generation !== this.generation) return;
    this.tokens = tokens;
    this.publish(user);
  }
  async register(fullName: string, email: string, password: string) {
    return this.send<User>("/auth/register", {
      method: "POST",
      body: JSON.stringify({ fullName, email, password, role: "STUDENT" }),
    });
  }
  async logout() {
    const token = this.tokens?.refreshToken;
    this.clear();
    if (token)
      await this.send<void>("/auth/logout", {
        method: "POST",
        body: JSON.stringify({ refreshToken: token }),
      });
  }
  async request<T>(path: string, options: RequestInit = {}): Promise<T> {
    const generation = this.generation;
    const token = this.tokens?.accessToken;
    if (!token) throw new ApiError(401, "Please sign in.");
    try {
      return await this.send<T>(path, options, token);
    } catch (error) {
      if (
        !(error instanceof ApiError) ||
        error.status !== 401 ||
        generation !== this.generation
      )
        throw error;
      if (this.tokens?.accessToken === token) {
        if (!this.refresh) {
          const current = this.tokens;
          const refreshing = this.send<Tokens>("/auth/refresh", {
            method: "POST",
            body: JSON.stringify({ refreshToken: current.refreshToken }),
          })
            .then((tokens) => {
              if (generation === this.generation) this.tokens = tokens;
            })
            .catch((error) => {
              if (generation === this.generation) this.clear();
              throw error;
            })
            .finally(() => {
              if (this.refresh === refreshing) this.refresh = null;
            });
          this.refresh = refreshing;
        }
        await this.refresh;
      }
      if (generation !== this.generation || !this.tokens)
        throw new ApiError(401, "Your session ended. Please sign in.");
      try {
        return await this.send<T>(path, options, this.tokens.accessToken);
      } catch (retryError) {
        if (
          retryError instanceof ApiError &&
          retryError.status === 401 &&
          generation === this.generation
        )
          this.clear();
        throw retryError;
      }
    }
  }
}
export const api = new ApiClient(
  import.meta.env.VITE_API_BASE_URL || "/api/v1",
);
export const post = (body: unknown): RequestInit => ({
  method: "POST",
  body: JSON.stringify(body),
});
export const message = (error: unknown) =>
  error instanceof Error
    ? error.message
    : "The request could not be completed.";
