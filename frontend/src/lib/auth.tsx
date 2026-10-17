"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";

import { api, ApiError, tokens } from "@/lib/api";
import { MeResponseSchema, type AuthResponse, type MeResponse } from "@/types";

interface AuthState {
  user: MeResponse | null;
  role: string | null;
  loading: boolean;
  isAuthenticated: boolean;
  login: (auth: AuthResponse) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthState | undefined>(undefined);

const ME_KEY = "nn_me";

function cacheMe(me: MeResponse | null) {
  try {
    if (me) window.localStorage.setItem(ME_KEY, JSON.stringify(me));
    else window.localStorage.removeItem(ME_KEY);
  } catch {
    // storage unavailable (private mode): offline sign-in simply isn't kept
  }
}

function readCachedMe(): MeResponse | null {
  try {
    const raw = window.localStorage.getItem(ME_KEY);
    return raw ? MeResponseSchema.parse(JSON.parse(raw)) : null;
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<MeResponse | null>(null);
  const [role, setRole] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const refreshUser = useCallback(async () => {
    if (!tokens.access) {
      setUser(null);
      setRole(null);
      return;
    }
    try {
      const me = MeResponseSchema.parse(await api<unknown>("/api/auth/me"));
      setUser(me);
      setRole(me.role);
      cacheMe(me);
    } catch (err) {
      // Offline (ADR 0009): a network failure is not a bad session. Keep the user signed in from the
      // last known profile so queued applies still work; only an HTTP error ends the session.
      const cached = !(err instanceof ApiError) ? readCachedMe() : null;
      if (cached) {
        setUser(cached);
        setRole(cached.role);
        return;
      }
      tokens.clear();
      cacheMe(null);
      setUser(null);
      setRole(null);
    }
  }, []);

  useEffect(() => {
    // Hydrate role from localStorage after mount (avoids an SSR/client hydration mismatch).
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setRole(tokens.role);
    void refreshUser().finally(() => setLoading(false));
  }, [refreshUser]);

  const login = useCallback(
    async (auth: AuthResponse) => {
      tokens.set(auth);
      setRole(auth.role);
      await refreshUser();
    },
    [refreshUser],
  );

  const logout = useCallback(async () => {
    const refresh = tokens.refresh;
    try {
      await api<void>("/api/auth/logout", {
        method: "POST",
        auth: false,
        body: refresh ? { refreshToken: refresh } : {},
      });
    } catch {
      // best-effort; clear local state regardless
    }
    tokens.clear();
    cacheMe(null);
    setUser(null);
    setRole(null);
  }, []);

  const value = useMemo<AuthState>(
    () => ({
      user,
      role,
      loading,
      isAuthenticated: !!user,
      login,
      logout,
      refreshUser,
    }),
    [user, role, loading, login, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within an AuthProvider");
  return ctx;
}
