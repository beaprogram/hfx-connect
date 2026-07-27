import { getJson, postJson, postNoContent } from "./client";
import { loginResponseSchema, userResponseSchema, type LoginResponse, type UserResponse } from "@/lib/validation/schemas";

export interface RegisterParams {
  email: string;
  password: string;
  signal?: AbortSignal;
}

/** Always creates a USER/ACTIVE account; does not log the caller in (see docs/api/README.md). */
export function register(params: RegisterParams): Promise<UserResponse> {
  const { email, password, signal } = params;
  return postJson("/api/v1/auth/register", userResponseSchema, { body: { email, password }, signal });
}

export interface LoginParams {
  email: string;
  password: string;
  signal?: AbortSignal;
}

/** `credentials: "include"` is required to receive the `hfx_refresh_token` `Set-Cookie` cross-origin (ADR-008/ADR-009). */
export function login(params: LoginParams): Promise<LoginResponse> {
  const { email, password, signal } = params;
  return postJson("/api/v1/auth/login", loginResponseSchema, {
    body: { email, password },
    credentials: "include",
    signal,
  });
}

/** Reads the refresh cookie only — never a token from JavaScript state (ADR-009). */
export function refreshSession(signal?: AbortSignal): Promise<LoginResponse> {
  return postJson("/api/v1/auth/refresh", loginResponseSchema, { credentials: "include", signal });
}

/** Always safe to call; the backend never requires an access token for logout. */
export function logout(signal?: AbortSignal): Promise<void> {
  return postNoContent("/api/v1/auth/logout", { credentials: "include", signal });
}

/** Requires a valid Bearer access token; returns the caller's own account only. */
export function getCurrentUser(accessToken: string, signal?: AbortSignal): Promise<UserResponse> {
  return getJson("/api/v1/users/me", userResponseSchema, { accessToken, signal });
}
