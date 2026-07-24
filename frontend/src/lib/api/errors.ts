/**
 * A non-2xx response from the API, or a network-level failure reaching it
 * (`status` is `0` for the latter — there was never an HTTP response to read
 * a real status from).
 */
export class ApiRequestError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly fieldErrors?: Record<string, string>;

  constructor(message: string, status: number, code?: string, fieldErrors?: Record<string, string>) {
    super(message);
    this.name = "ApiRequestError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

/**
 * A 2xx response whose body did not match the schema this frontend expects —
 * a backend/frontend contract drift, not a normal "request failed" case, so
 * it's kept distinct from {@link ApiRequestError}.
 */
export class ApiResponseShapeError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ApiResponseShapeError";
  }
}

export function isAbortError(error: unknown): boolean {
  // Not `instanceof Error`: DOMException (what a real aborted fetch throws)
  // is only reliably identifiable by its `name`, not its prototype chain,
  // across browser/jsdom/Node implementations.
  return typeof error === "object" && error !== null && (error as { name?: unknown }).name === "AbortError";
}
