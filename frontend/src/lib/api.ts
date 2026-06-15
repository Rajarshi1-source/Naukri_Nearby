import type { AuthResponse, ProblemDetail } from "@/types";

export const API_URL =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

const ACCESS_KEY = "nn_access";
const REFRESH_KEY = "nn_refresh";
const ROLE_KEY = "nn_role";
const SESSION_COOKIE = "nn_session";

/**
 * In-memory access token plus localStorage-backed refresh token. The access token is deliberately
 * kept out of localStorage where practical, but we also persist it so a full reload stays signed in
 * for this MVP; production could move to httpOnly cookies.
 */
let accessToken: string | null = null;

function isBrowser(): boolean {
  return typeof window !== "undefined";
}

/**
 * Non-sensitive presence marker read by src/proxy.ts to gate /candidate and /employer routes.
 * It only signals "a session likely exists" — real JWT verification stays at the API.
 */
function setSessionCookie() {
  if (isBrowser()) {
    document.cookie = `${SESSION_COOKIE}=1; path=/; max-age=2592000; SameSite=Lax`;
  }
}

function clearSessionCookie() {
  if (isBrowser()) {
    document.cookie = `${SESSION_COOKIE}=; path=/; max-age=0; SameSite=Lax`;
  }
}

export const tokens = {
  get access(): string | null {
    if (accessToken) return accessToken;
    if (isBrowser()) accessToken = window.localStorage.getItem(ACCESS_KEY);
    return accessToken;
  },
  get refresh(): string | null {
    return isBrowser() ? window.localStorage.getItem(REFRESH_KEY) : null;
  },
  get role(): string | null {
    return isBrowser() ? window.localStorage.getItem(ROLE_KEY) : null;
  },
  set(auth: AuthResponse) {
    accessToken = auth.accessToken;
    if (isBrowser()) {
      window.localStorage.setItem(ACCESS_KEY, auth.accessToken);
      window.localStorage.setItem(REFRESH_KEY, auth.refreshToken);
      window.localStorage.setItem(ROLE_KEY, auth.role);
    }
    setSessionCookie();
  },
  clear() {
    accessToken = null;
    if (isBrowser()) {
      window.localStorage.removeItem(ACCESS_KEY);
      window.localStorage.removeItem(REFRESH_KEY);
      window.localStorage.removeItem(ROLE_KEY);
    }
    clearSessionCookie();
  },
};

export class ApiError extends Error {
  status: number;
  problem?: ProblemDetail;

  constructor(status: number, message: string, problem?: ProblemDetail) {
    super(message);
    this.status = status;
    this.problem = problem;
  }
}

async function parseError(res: Response): Promise<ApiError> {
  let problem: ProblemDetail | undefined;
  try {
    problem = (await res.json()) as ProblemDetail;
  } catch {
    problem = undefined;
  }
  const message =
    problem?.detail || problem?.title || `Request failed (${res.status})`;
  return new ApiError(res.status, message, problem);
}

async function refreshAccessToken(): Promise<boolean> {
  const refresh = tokens.refresh;
  if (!refresh) return false;
  const res = await fetch(`${API_URL}/api/auth/refresh`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken: refresh }),
  });
  if (!res.ok) {
    tokens.clear();
    return false;
  }
  tokens.set((await res.json()) as AuthResponse);
  return true;
}

interface ApiOptions extends Omit<RequestInit, "body"> {
  body?: unknown;
  auth?: boolean;
  multipart?: boolean;
}

/**
 * Browser-side API call. Attaches the Bearer token when `auth` is set, parses ProblemDetail errors,
 * and transparently refreshes the access token once on a 401 before retrying.
 */
export async function api<T>(path: string, opts: ApiOptions = {}): Promise<T> {
  const { auth = true, multipart = false, body, headers, ...rest } = opts;

  const buildInit = (): RequestInit => {
    const h = new Headers(headers);
    if (auth && tokens.access) h.set("Authorization", `Bearer ${tokens.access}`);
    let payload: BodyInit | undefined;
    if (body instanceof FormData) {
      payload = body;
    } else if (body !== undefined) {
      if (!multipart) h.set("Content-Type", "application/json");
      payload = JSON.stringify(body);
    }
    return { ...rest, headers: h, body: payload };
  };

  let res = await fetch(`${API_URL}${path}`, buildInit());

  if (res.status === 401 && auth && tokens.refresh) {
    const refreshed = await refreshAccessToken();
    if (refreshed) {
      res = await fetch(`${API_URL}${path}`, buildInit());
    }
  }

  if (!res.ok) throw await parseError(res);
  if (res.status === 204) return undefined as T;

  const contentType = res.headers.get("content-type") ?? "";
  if (!contentType.includes("application/json")) return undefined as T;
  return (await res.json()) as T;
}

/** Server-side fetch for SSR of public endpoints (no auth). Returns null on 404. */
export async function serverGet<T>(path: string): Promise<T | null> {
  const res = await fetch(`${API_URL}${path}`, { cache: "no-store" });
  if (res.status === 404) return null;
  if (!res.ok) throw await parseError(res);
  return (await res.json()) as T;
}
