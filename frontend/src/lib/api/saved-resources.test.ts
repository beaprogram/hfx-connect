import { saveResource, removeSavedResource, getSavedResources, getSavedResourceStatus } from "./saved-resources";
import { ApiRequestError } from "./errors";

const resourceSummary = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Halifax Central Library",
  slug: "halifax-central-library",
  city: "Halifax",
  province: "NS",
  costType: "FREE",
  verificationStatus: "UNVERIFIED",
  active: true,
  category: { id: 1, name: "Study Spaces", slug: "study-spaces" },
  createdAt: "2026-07-15T00:00:00Z",
  hoursStatus: "UNKNOWN",
  openNow: null,
};

describe("saveResource", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("sends a PUT request to the current-user saved-resources endpoint with a Bearer token", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 204 });

    await saveResource("11111111-1111-1111-1111-111111111111", "token-1");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe(
      "/api/v1/users/me/saved-resources/11111111-1111-1111-1111-111111111111",
    );
    expect(init.method).toBe("PUT");
    expect(init.headers.Authorization).toBe("Bearer token-1");
  });

  it("URL-encodes the resource id", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 204 });

    await saveResource("id with spaces", "token-1");

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain("id%20with%20spaces");
  });

  it("propagates a 404 as ApiRequestError", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 404,
      json: async () => ({ status: 404, code: "RESOURCE_NOT_FOUND", message: "No resource." }),
    });

    const error = await saveResource("x", "token-1").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).status).toBe(404);
  });

  it("never sends a user id in the request", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 204 });

    await saveResource("11111111-1111-1111-1111-111111111111", "token-1");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).not.toContain("userId");
    expect(JSON.stringify(init.body ?? "")).not.toContain("userId");
  });
});

describe("removeSavedResource", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("sends a DELETE request to the current-user saved-resources endpoint", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 204 });

    await removeSavedResource("11111111-1111-1111-1111-111111111111", "token-1");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe(
      "/api/v1/users/me/saved-resources/11111111-1111-1111-1111-111111111111",
    );
    expect(init.method).toBe("DELETE");
  });

  it("resolves successfully (idempotent) even when repeated", async () => {
    (global.fetch as jest.Mock).mockResolvedValue({ ok: true, status: 204 });

    await expect(removeSavedResource("x", "token-1")).resolves.toBeUndefined();
    await expect(removeSavedResource("x", "token-1")).resolves.toBeUndefined();
  });
});

describe("getSavedResources", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("requests the saved-resource list with page/size/sort and a Bearer token", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        content: [{ savedAt: "2026-08-01T00:00:00Z", resource: resourceSummary }],
        page: 0,
        size: 10,
        totalElements: 1,
        totalPages: 1,
      }),
    });

    const result = await getSavedResources({ page: 1, size: 10, sort: "name", accessToken: "token-1" });

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    const requestedUrl = new URL(url as string);
    expect(requestedUrl.pathname).toBe("/api/v1/users/me/saved-resources");
    expect(requestedUrl.searchParams.get("page")).toBe("1");
    expect(requestedUrl.searchParams.get("size")).toBe("10");
    expect(requestedUrl.searchParams.get("sort")).toBe("name");
    expect(init.headers.Authorization).toBe("Bearer token-1");
    expect(result.content[0]!.resource.name).toBe("Halifax Central Library");
  });

  it("defaults page/size when not provided", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    });

    await getSavedResources({ accessToken: "token-1" });

    const requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.searchParams.get("page")).toBe("0");
    expect(requestedUrl.searchParams.get("size")).toBe("20");
  });

  it("propagates a 401 as ApiRequestError", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 401,
      json: async () => ({ status: 401, code: "AUTHENTICATION_REQUIRED", message: "Auth required." }),
    });

    const error = await getSavedResources({ accessToken: "bad-token" }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).status).toBe(401);
  });
});

describe("getSavedResourceStatus", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("sends a POST request with the resource ids and a Bearer token", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ savedResourceIds: ["11111111-1111-1111-1111-111111111111"] }),
    });

    const result = await getSavedResourceStatus(
      ["11111111-1111-1111-1111-111111111111", "22222222-2222-2222-2222-222222222222"],
      "token-1",
    );

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe("/api/v1/users/me/saved-resources/status");
    expect(init.method).toBe("POST");
    expect(init.headers.Authorization).toBe("Bearer token-1");
    const body = JSON.parse(init.body as string);
    expect(body.resourceIds).toEqual([
      "11111111-1111-1111-1111-111111111111",
      "22222222-2222-2222-2222-222222222222",
    ]);
    expect(result.savedResourceIds).toEqual(["11111111-1111-1111-1111-111111111111"]);
  });

  it("deduplicates resource ids client-side before sending", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ savedResourceIds: [] }),
    });

    await getSavedResourceStatus(["a", "a", "b"], "token-1");

    const [, init] = (global.fetch as jest.Mock).mock.calls[0];
    const body = JSON.parse(init.body as string);
    expect(body.resourceIds).toEqual(["a", "b"]);
  });

  it("propagates a 400 as ApiRequestError", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 400,
      json: async () => ({ status: 400, code: "VALIDATION_ERROR", message: "Too many ids." }),
    });

    const error = await getSavedResourceStatus(["a"], "token-1").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).status).toBe(400);
  });
});
