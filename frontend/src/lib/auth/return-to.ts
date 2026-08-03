/**
 * Validates a caller-supplied "return to this page after login" path
 * (Milestone 8A's "Sign in to save" flow — see ADR/milestone doc). Only a
 * same-origin, relative path beginning with a single `/` is ever accepted;
 * everything else — an absolute URL, a protocol-relative `//host` value, a
 * backslash trick some browsers still normalize to `//`, or anything that
 * doesn't resolve to this same origin — is rejected. This is the only
 * defense against an open redirect through the login flow, so it must fail
 * closed on anything ambiguous.
 */
export function isSafeReturnPath(path: string | null | undefined): path is string {
  if (!path || path.length === 0) {
    return false;
  }
  if (!path.startsWith("/") || path.startsWith("//") || path.startsWith("/\\")) {
    return false;
  }
  try {
    // Resolving against a fixed, arbitrary base and checking the origin is
    // unchanged is what actually catches every absolute-URL/scheme trick —
    // a plain string-prefix check alone cannot reliably rule those out.
    const resolved = new URL(path, "https://return-to.invalid");
    return resolved.origin === "https://return-to.invalid";
  } catch {
    return false;
  }
}

/** Resolves an untrusted `returnTo` value to a safe redirect target, falling back when it's missing or unsafe. */
export function resolveReturnPath(returnTo: string | null | undefined, fallback: string): string {
  return isSafeReturnPath(returnTo) ? returnTo : fallback;
}

/** Builds a `/login` href carrying a safe return path, for "Sign in to save" links on public pages. Omits the parameter entirely when the path isn't safe. */
export function buildLoginHref(returnTo: string): string {
  return isSafeReturnPath(returnTo) ? `/login?returnTo=${encodeURIComponent(returnTo)}` : "/login";
}
