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

import { api, tokens } from "./api";
import type { AuthResponse, MeResponse } from "./types";

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
      const me = await api<MeResponse>("/api/auth/me");
      setUser(me);
      setRole(me.role);
    } catch {
      tokens.clear();
      setUser(null);
      setRole(null);
    }
  }, []);

  useEffect(() => {
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
