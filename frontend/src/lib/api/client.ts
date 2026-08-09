import type { ZodType } from "zod";
import { apiErrorSchema } from "@/lib/validation/schemas";
import { ApiRequestError, ApiResponseShapeError, isAbortError } from "./errors";

/**
 * Not a secret — see frontend/README.md's "Environment Configuration" section
 * for why only non-sensitive values may ever live in a NEXT_PUBLIC_ variable.
 */
const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

type QueryParamValue = string | number | boolean | undefined | null;
type QueryParams = Record<string, QueryParamValue>;

function buildUrl(path: string, params?: QueryParams): string {
  const url = new URL(path, API_BASE_URL);
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined && value !== null) {
        url.searchParams.set(key, String(value));
      }
    }
  }
  return url.toString();
}

interface GetJsonOptions {
  params?: QueryParams;
  signal?: AbortSignal;
  /** Attaches `Authorization: Bearer <accessToken>` — for endpoints that require a signed-in caller (e.g. `/api/v1/users/me`). */
  accessToken?: string;
}

/**
 * The single place every GET request to the backend goes through: URL/query
 * construction, non-2xx error mapping, and runtime response-shape validation.
 * Nothing else in this codebase should call `fetch` against the backend
 * directly.
 */
export async function getJson<T>(path: string, schema: ZodType<T>, options: GetJsonOptions = {}): Promise<T> {
  const url = buildUrl(path, options.params);

  const response = await sendRequest(url, {
    signal: options.signal,
    cache: "no-store",
    headers: authorizedHeaders({ Accept: "application/json" }, options.accessToken),
  });

  return parseJsonResponse(response, path, schema);
}

interface JsonRequestOptions {
  signal?: AbortSignal;
  /**
   * `"include"` sends and allows receiving the `hfx_refresh_token` cookie on
   * a cross-origin request — required for login/refresh/logout, and only
   * legal because the backend's CORS policy allows credentials for this
   * exact configured origin, never a wildcard (ADR-006, ADR-008).
   */
  credentials?: RequestCredentials;
  /** Attaches `Authorization: Bearer <accessToken>` for authenticated operations. */
  accessToken?: string;
  /** Omit entirely for a request with no body (e.g. refresh, logout). */
  body?: unknown;
}

/**
 * The single place every POST-with-a-JSON-response request goes through —
 * the `postJson` counterpart to {@link getJson}. Used for register, login,
 * and refresh, each of which return a real body the caller needs validated.
 */
export async function postJson<T>(path: string, schema: ZodType<T>, options: JsonRequestOptions = {}): Promise<T> {
  const url = buildUrl(path);
  const response = await sendJsonRequest(url, "POST", options);
  return parseJsonResponse(response, path, schema);
}

/** The `patchJson` counterpart to {@link postJson} — used for a partial update that returns the updated resource (e.g. an organization profile edit, Milestone 10A). */
export async function patchJson<T>(path: string, schema: ZodType<T>, options: JsonRequestOptions = {}): Promise<T> {
  const url = buildUrl(path);
  const response = await sendJsonRequest(url, "PATCH", options);
  return parseJsonResponse(response, path, schema);
}

/**
 * The `postJson` counterpart for a request with no meaningful response body
 * (logout returns `204 No Content`).
 */
export async function postNoContent(path: string, options: JsonRequestOptions = {}): Promise<void> {
  const url = buildUrl(path);
  const response = await sendJsonRequest(url, "POST", options);
  if (!response.ok) {
    throw await toApiRequestError(response);
  }
}

/**
 * `PUT` with no meaningful response body — the current-user saved-resource
 * "ensure saved" endpoint (Milestone 8A) returns `204 No Content` either way
 * (whether this call created the relation or it already existed), so there
 * is nothing to parse as JSON on success.
 */
export async function putNoContent(path: string, options: JsonRequestOptions = {}): Promise<void> {
  const url = buildUrl(path);
  const response = await sendJsonRequest(url, "PUT", options);
  if (!response.ok) {
    throw await toApiRequestError(response);
  }
}

/**
 * `DELETE` with no meaningful response body — the current-user
 * "ensure not saved" endpoint (Milestone 8A) returns `204 No Content`
 * whether it removed a relation or the relation was already absent.
 */
export async function deleteNoContent(path: string, options: JsonRequestOptions = {}): Promise<void> {
  const url = buildUrl(path);
  const response = await sendJsonRequest(url, "DELETE", options);
  if (!response.ok) {
    throw await toApiRequestError(response);
  }
}

function sendJsonRequest(
  url: string,
  method: "POST" | "PUT" | "PATCH" | "DELETE",
  options: JsonRequestOptions,
): Promise<Response> {
  const headers = authorizedHeaders({ Accept: "application/json" }, options.accessToken);
  let requestBody: string | undefined;
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    requestBody = JSON.stringify(options.body);
  }
  return sendRequest(url, {
    method,
    headers,
    body: requestBody,
    credentials: options.credentials,
    signal: options.signal,
  });
}

function authorizedHeaders(base: Record<string, string>, accessToken: string | undefined): Record<string, string> {
  return accessToken ? { ...base, Authorization: `Bearer ${accessToken}` } : base;
}

async function sendRequest(url: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(url, init);
  } catch (cause) {
    if (isAbortError(cause)) {
      throw cause;
    }
    throw new ApiRequestError("Could not reach the HFX Connect API.", 0);
  }
}

async function parseJsonResponse<T>(response: Response, path: string, schema: ZodType<T>): Promise<T> {
  if (!response.ok) {
    throw await toApiRequestError(response);
  }

  let body: unknown;
  try {
    body = await response.json();
  } catch {
    throw new ApiResponseShapeError("The API returned a response that was not valid JSON.");
  }

  const result = schema.safeParse(body);
  if (!result.success) {
    if (process.env.NODE_ENV !== "production") {
      // Local diagnosis only — the thrown error carries no schema/field detail to the UI.
      console.error(`API response from ${path} failed shape validation:`, result.error.issues);
    }
    throw new ApiResponseShapeError("The API returned a response in an unexpected shape.");
  }

  return result.data;
}

async function toApiRequestError(response: Response): Promise<ApiRequestError> {
  try {
    const body: unknown = await response.json();
    const parsed = apiErrorSchema.safeParse(body);
    if (parsed.success) {
      return new ApiRequestError(
        parsed.data.message,
        parsed.data.status,
        parsed.data.code,
        parsed.data.fieldErrors ?? undefined,
      );
    }
  } catch {
    // Body wasn't JSON, or didn't match ApiErrorBody — fall through to the generic error below.
  }
  return new ApiRequestError(`The API returned an unexpected error (HTTP ${response.status}).`, response.status);
}
