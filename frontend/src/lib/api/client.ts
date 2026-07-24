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
}

/**
 * The single place every GET request to the backend goes through: URL/query
 * construction, non-2xx error mapping, and runtime response-shape validation.
 * Nothing else in this codebase should call `fetch` against the backend
 * directly.
 */
export async function getJson<T>(path: string, schema: ZodType<T>, options: GetJsonOptions = {}): Promise<T> {
  const url = buildUrl(path, options.params);

  let response: Response;
  try {
    response = await fetch(url, {
      signal: options.signal,
      cache: "no-store",
      headers: { Accept: "application/json" },
    });
  } catch (cause) {
    if (isAbortError(cause)) {
      throw cause;
    }
    throw new ApiRequestError("Could not reach the HFX Connect API.", 0);
  }

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
