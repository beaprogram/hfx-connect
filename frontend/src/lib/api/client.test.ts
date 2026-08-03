import { z } from "zod";
import { getJson, postJson, postNoContent, putNoContent, deleteNoContent } from "./client";
import { ApiRequestError, ApiResponseShapeError } from "./errors";

const testSchema = z.object({ id: z.number(), name: z.string() });

function mockFetchOnce(response: Partial<Response> & { json?: () => Promise<unknown> }) {
  const fullResponse = {
    ok: true,
    status: 200,
    json: async () => ({}),
    ...response,
  } as Response;
  (global.fetch as jest.Mock).mockResolvedValueOnce(fullResponse);
}

describe("getJson", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("parses and returns a well-formed response", async () => {
    mockFetchOnce({ ok: true, status: 200, json: async () => ({ id: 1, name: "Food Assistance" }) });

    const result = await getJson("/api/v1/categories/1", testSchema);

    expect(result).toEqual({ id: 1, name: "Food Assistance" });
  });

  it("encodes query parameters and omits undefined/null ones", async () => {
    mockFetchOnce({ ok: true, status: 200, json: async () => ({ id: 1, name: "x" }) });

    await getJson("/api/v1/resources", testSchema, {
      params: { page: 0, categoryId: undefined, sort: null, active: true },
    });

    const requestedUrl = (global.fetch as jest.Mock).mock.calls[0][0] as string;
    const url = new URL(requestedUrl);
    expect(url.searchParams.get("page")).toBe("0");
    expect(url.searchParams.has("categoryId")).toBe(false);
    expect(url.searchParams.has("sort")).toBe(false);
    expect(url.searchParams.get("active")).toBe("true");
  });

  it("throws ApiRequestError with the backend's ApiError body on a non-2xx response", async () => {
    mockFetchOnce({
      ok: false,
      status: 404,
      json: async () => ({
        timestamp: "2026-07-24T00:00:00Z",
        status: 404,
        code: "RESOURCE_NOT_FOUND",
        message: "No resource exists with that slug.",
      }),
    });

    await expect(getJson("/api/v1/resources/slug/unknown", testSchema)).rejects.toMatchObject({
      status: 404,
      code: "RESOURCE_NOT_FOUND",
      message: "No resource exists with that slug.",
    });
  });

  it("throws a generic ApiRequestError when the error response body doesn't match ApiError", async () => {
    mockFetchOnce({ ok: false, status: 502, json: async () => "not json shaped like an error" });

    const error = await getJson("/api/v1/resources", testSchema).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).status).toBe(502);
  });

  it("throws ApiRequestError with status 0 when the network request itself fails", async () => {
    (global.fetch as jest.Mock).mockRejectedValueOnce(new Error("network down"));

    const error = await getJson("/api/v1/resources", testSchema).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).status).toBe(0);
  });

  it("throws ApiResponseShapeError when the response body doesn't match the expected schema", async () => {
    mockFetchOnce({ ok: true, status: 200, json: async () => ({ id: "not-a-number" }) });

    await expect(getJson("/api/v1/categories/1", testSchema)).rejects.toBeInstanceOf(ApiResponseShapeError);
  });

  it("lets an AbortError propagate as-is rather than wrapping it", async () => {
    const abortError = new DOMException("The operation was aborted.", "AbortError");
    (global.fetch as jest.Mock).mockRejectedValueOnce(abortError);

    await expect(getJson("/api/v1/resources", testSchema)).rejects.toBe(abortError);
  });

  it("attaches an Authorization: Bearer header when accessToken is provided", async () => {
    mockFetchOnce({ ok: true, status: 200, json: async () => ({ id: 1, name: "x" }) });

    await getJson("/api/v1/users/me", testSchema, { accessToken: "a-token" });

    const [, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer a-token");
  });

  it("sends no Authorization header when accessToken is omitted", async () => {
    mockFetchOnce({ ok: true, status: 200, json: async () => ({ id: 1, name: "x" }) });

    await getJson("/api/v1/categories/1", testSchema);

    const [, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect((init.headers as Record<string, string>).Authorization).toBeUndefined();
  });
});

describe("postJson", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("sends a JSON body with Content-Type set", async () => {
    mockFetchOnce({ ok: true, status: 200, json: async () => ({ id: 1, name: "x" }) });

    await postJson("/api/v1/auth/login", testSchema, { body: { email: "a@example.org", password: "secret123" } });

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain("/api/v1/auth/login");
    expect(init.method).toBe("POST");
    expect((init.headers as Record<string, string>)["Content-Type"]).toBe("application/json");
    expect(JSON.parse(init.body as string)).toEqual({ email: "a@example.org", password: "secret123" });
  });

  it("omits the body and Content-Type entirely when none is given", async () => {
    mockFetchOnce({ ok: true, status: 200, json: async () => ({ id: 1, name: "x" }) });

    await postJson("/api/v1/auth/refresh", testSchema, { credentials: "include" });

    const [, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(init.body).toBeUndefined();
    expect((init.headers as Record<string, string>)["Content-Type"]).toBeUndefined();
    expect(init.credentials).toBe("include");
  });

  it("attaches an Authorization: Bearer header when accessToken is provided", async () => {
    mockFetchOnce({ ok: true, status: 200, json: async () => ({ id: 1, name: "x" }) });

    await postJson("/api/v1/some-authenticated-post", testSchema, { accessToken: "a-token" });

    const [, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect((init.headers as Record<string, string>).Authorization).toBe("Bearer a-token");
  });

  it("throws ApiRequestError with the backend's ApiError body on a non-2xx response", async () => {
    mockFetchOnce({
      ok: false,
      status: 401,
      json: async () => ({
        timestamp: "2026-07-27T00:00:00Z",
        status: 401,
        code: "AUTHENTICATION_FAILED",
        message: "Invalid email or password.",
      }),
    });

    await expect(postJson("/api/v1/auth/login", testSchema, { body: {} })).rejects.toMatchObject({
      status: 401,
      code: "AUTHENTICATION_FAILED",
    });
  });
});

describe("postNoContent", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("resolves without attempting to parse a body on success", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 204 } as Response);

    await expect(postNoContent("/api/v1/auth/logout", { credentials: "include" })).resolves.toBeUndefined();

    const [, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(init.credentials).toBe("include");
  });

  it("throws ApiRequestError on a non-2xx response", async () => {
    mockFetchOnce({ ok: false, status: 500, json: async () => ({ status: 500, code: "INTERNAL_ERROR", message: "Oops." }) });

    await expect(postNoContent("/api/v1/auth/logout")).rejects.toBeInstanceOf(ApiRequestError);
  });
});

describe("putNoContent", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("sends a PUT request and resolves without attempting to parse a body on success", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 204 } as Response);

    await expect(
      putNoContent("/api/v1/users/me/saved-resources/11111111-1111-1111-1111-111111111111", {
        accessToken: "token-1",
      }),
    ).resolves.toBeUndefined();

    const [, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(init.method).toBe("PUT");
    expect(init.headers.Authorization).toBe("Bearer token-1");
  });

  it("throws ApiRequestError on a non-2xx response", async () => {
    mockFetchOnce({ ok: false, status: 404, json: async () => ({ status: 404, code: "RESOURCE_NOT_FOUND", message: "Not found." }) });

    await expect(putNoContent("/api/v1/users/me/saved-resources/x", { accessToken: "token-1" })).rejects.toBeInstanceOf(
      ApiRequestError,
    );
  });
});

describe("deleteNoContent", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("sends a DELETE request and resolves without attempting to parse a body on success", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 204 } as Response);

    await expect(
      deleteNoContent("/api/v1/users/me/saved-resources/11111111-1111-1111-1111-111111111111", {
        accessToken: "token-1",
      }),
    ).resolves.toBeUndefined();

    const [, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(init.method).toBe("DELETE");
    expect(init.headers.Authorization).toBe("Bearer token-1");
  });

  it("throws ApiRequestError on a non-2xx response", async () => {
    mockFetchOnce({ ok: false, status: 401, json: async () => ({ status: 401, code: "AUTHENTICATION_REQUIRED", message: "Auth required." }) });

    await expect(deleteNoContent("/api/v1/users/me/saved-resources/x")).rejects.toBeInstanceOf(ApiRequestError);
  });
});
