"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useQueryClient, type QueryClient } from "@tanstack/react-query";
import { login as apiLogin, logout as apiLogout, refreshSession } from "@/lib/api/auth";
import type { UserResponse } from "@/lib/validation/schemas";

/**
 * Every query-key prefix that roots private, per-account data — see
 * `lib/query/keys.ts`'s `savedResourceKeys`/`resourceSubmissionKeys`/
 * `correctionReportKeys`, each of which starts with one of these strings
 * followed by a `userId`. Cleared unconditionally on logout and on a
 * detected account switch, never left to go stale.
 */
const PRIVATE_QUERY_KEY_PREFIXES = ["saved-resources", "resource-submissions", "correction-reports"] as const;

function clearPrivateContributionCaches(queryClient: QueryClient): void {
  for (const prefix of PRIVATE_QUERY_KEY_PREFIXES) {
    queryClient.removeQueries({ queryKey: [prefix] });
  }
}

/**
 * A small clock-skew/round-trip buffer: a token is treated as due for
 * refresh slightly before its actual `expiresAt`, so a request never starts
 * with a token that expires mid-flight. See ADR-009's "Token Expiry
 * Handling."
 */
const EXPIRY_BUFFER_MS = 10_000;

export type AuthState =
  | { status: "loading" }
  | { status: "authenticated"; user: UserResponse; accessToken: string; expiresAt: number }
  | { status: "unauthenticated" };

interface AuthContextValue {
  state: AuthState;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /**
   * Returns a token known to be valid for at least a few more seconds,
   * refreshing first if necessary. Returns `null` if no session could be
   * established (no caller should treat this as an error to surface loudly —
   * it means "signed out," which is an ordinary state).
   */
  getValidAccessToken: () => Promise<string | null>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}

/**
 * Owns the frontend's entire in-memory session: the access token never
 * touches `localStorage`/`sessionStorage`, and the refresh token is never
 * visible to this code at all (it is an `HttpOnly` cookie the browser
 * manages) — see ADR-009's "Frontend: Access Token In Memory Only" section.
 * Every reload therefore starts from `"loading"` and attempts exactly one
 * session-restoration refresh; a page that never had a session simply lands
 * on `"unauthenticated"`, which is not an error state.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: "loading" });
  const queryClient = useQueryClient();

  // Refresh tokens rotate on every use (ADR-008) — two concurrent refresh
  // attempts from this tab could each present the same soon-to-be-rotated
  // cookie and trigger a false-positive family-wide revocation. Every caller
  // awaits this single in-flight promise instead of issuing its own request.
  const refreshPromiseRef = useRef<Promise<string | null> | null>(null);
  // The previously-authenticated account's id, if any — lets applySession
  // detect a genuine account switch (not just a routine token refresh for
  // the same account) without adding an extra state field.
  const previousUserIdRef = useRef<string | null>(null);

  const applySession = useCallback(
    (accessToken: string, expiresInSeconds: number, user: UserResponse) => {
      // Every private-data query key is rooted in userId (see
      // lib/query/keys.ts), so a same-account refresh never collides with
      // this. This clear is specifically for a genuine account switch
      // (sign out, then sign in as someone else in the same tab/session) —
      // without it, a stale cache entry for the previous account would
      // simply sit unused (harmless on its own), but clearing it
      // immediately is the more defensible, explicit guarantee: this
      // browser's TanStack Query cache never holds another account's
      // private data (saved resources, Milestone 8A; resource submissions
      // and correction reports, Milestone 8B) one render longer than
      // necessary.
      if (previousUserIdRef.current !== null && previousUserIdRef.current !== user.id) {
        clearPrivateContributionCaches(queryClient);
      }
      previousUserIdRef.current = user.id;
      setState({
        status: "authenticated",
        user,
        accessToken,
        expiresAt: Date.now() + expiresInSeconds * 1000,
      });
    },
    [queryClient],
  );

  const performRefresh = useCallback(async (): Promise<string | null> => {
    try {
      const result = await refreshSession();
      applySession(result.accessToken, result.expiresIn, result.user);
      return result.accessToken;
    } catch {
      // No valid refresh cookie (never had one, expired, or reused) — an
      // ordinary signed-out outcome, not logged or surfaced as an error.
      setState({ status: "unauthenticated" });
      return null;
    }
  }, [applySession]);

  const refreshOnce = useCallback((): Promise<string | null> => {
    refreshPromiseRef.current ??= performRefresh().finally(() => {
      refreshPromiseRef.current = null;
    });
    return refreshPromiseRef.current;
  }, [performRefresh]);

  useEffect(() => {
    void refreshOnce();
    // Restore the session exactly once, on initial mount only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = useCallback(
    async (email: string, password: string) => {
      const result = await apiLogin({ email, password });
      applySession(result.accessToken, result.expiresIn, result.user);
    },
    [applySession],
  );

  const logout = useCallback(async () => {
    try {
      await apiLogout();
    } catch {
      // Logout is unconditional from the frontend's perspective: even if the
      // network call fails, the in-memory session is forgotten regardless.
    } finally {
      // Saved resources (Milestone 8A), resource submissions, and
      // correction reports (Milestone 8B) are all private account data —
      // removed from the TanStack Query cache unconditionally on logout,
      // not just left to go stale, regardless of whether the network call
      // above succeeded.
      clearPrivateContributionCaches(queryClient);
      previousUserIdRef.current = null;
      setState({ status: "unauthenticated" });
    }
  }, [queryClient]);

  const getValidAccessToken = useCallback((): Promise<string | null> => {
    if (state.status === "authenticated" && Date.now() < state.expiresAt - EXPIRY_BUFFER_MS) {
      return Promise.resolve(state.accessToken);
    }
    return refreshOnce();
  }, [state, refreshOnce]);

  const value = useMemo<AuthContextValue>(
    () => ({ state, login, logout, getValidAccessToken }),
    [state, login, logout, getValidAccessToken],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
