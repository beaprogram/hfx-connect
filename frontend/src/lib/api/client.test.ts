import { z } from "zod";
import { getJson } from "./client";
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
});
