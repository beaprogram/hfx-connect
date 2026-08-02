import { getResources, getResourceBySlug, getNearbyResources } from "./resources";
import { ApiRequestError, ApiResponseShapeError } from "./errors";

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

describe("getResources", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("requests the resource list with the given filter/sort/pagination params", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [resourceSummary], page: 0, size: 12, totalElements: 1, totalPages: 1 }),
    });

    await getResources({ page: 0, size: 12, categoryId: 3, sort: "createdAt" });

    const requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.pathname).toBe("/api/v1/resources");
    expect(requestedUrl.searchParams.get("page")).toBe("0");
    expect(requestedUrl.searchParams.get("size")).toBe("12");
    expect(requestedUrl.searchParams.get("categoryId")).toBe("3");
    expect(requestedUrl.searchParams.get("sort")).toBe("createdAt");
  });

  it("omits categoryId and sort when not provided", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }),
    });

    await getResources();

    const requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.searchParams.has("categoryId")).toBe(false);
    expect(requestedUrl.searchParams.has("sort")).toBe(false);
  });

  it("encodes q and combines it with categoryId/sort/page", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }),
    });

    await getResources({ q: "food & bank", categoryId: 3, sort: "name", page: 1 });

    const requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.searchParams.get("q")).toBe("food & bank");
    expect(requestedUrl.searchParams.get("categoryId")).toBe("3");
    expect(requestedUrl.searchParams.get("page")).toBe("1");
  });

  it("omits q entirely when blank or not provided", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }),
    });
    await getResources({ q: "" });
    let requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.searchParams.has("q")).toBe(false);

    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }),
    });
    await getResources();
    requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[1][0] as string);
    expect(requestedUrl.searchParams.has("q")).toBe(false);
  });

  // ---- Cost/verification/openNow filters (Milestone 6B — see ADR-011) ----

  it("encodes costType and verificationStatus, and combines them with q/sort/page", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }),
    });

    await getResources({ q: "library", costType: "FREE", verificationStatus: "VERIFIED", sort: "name", page: 1 });

    const requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.searchParams.get("q")).toBe("library");
    expect(requestedUrl.searchParams.get("costType")).toBe("FREE");
    expect(requestedUrl.searchParams.get("verificationStatus")).toBe("VERIFIED");
    expect(requestedUrl.searchParams.get("sort")).toBe("name");
    expect(requestedUrl.searchParams.get("page")).toBe("1");
  });

  it("encodes openNow=true only when true; omits it when false or absent", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }),
    });
    await getResources({ openNow: true });
    let requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.searchParams.get("openNow")).toBe("true");

    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }),
    });
    await getResources({ openNow: false });
    requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[1][0] as string);
    expect(requestedUrl.searchParams.has("openNow")).toBe(false);

    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }),
    });
    await getResources();
    requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[2][0] as string);
    expect(requestedUrl.searchParams.has("openNow")).toBe(false);
  });

  it("parses hoursStatus and openNow on each resource summary in the response", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [resourceSummary], page: 0, size: 12, totalElements: 1, totalPages: 1 }),
    });

    const result = await getResources();

    expect(result.content[0]!.hoursStatus).toBe("UNKNOWN");
    expect(result.content[0]!.openNow).toBeNull();
  });
});

describe("getResourceBySlug", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("URL-encodes the slug and requests the slug endpoint", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        ...resourceSummary,
        description: null,
        addressLine1: null,
        addressLine2: null,
        postalCode: null,
        phone: null,
        email: null,
        websiteUrl: null,
        costDetails: null,
        eligibility: null,
        updatedAt: "2026-07-15T00:00:00Z",
        hours: { timezone: "America/Halifax", weeklyHours: [], hoursStatus: "UNKNOWN", openNow: null },
      }),
    });

    await getResourceBySlug("a slug/with special?chars");

    const requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.pathname).toBe("/api/v1/resources/slug/a%20slug%2Fwith%20special%3Fchars");
  });

  it("parses a full weekly schedule from the response", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        ...resourceSummary,
        description: null,
        addressLine1: null,
        addressLine2: null,
        postalCode: null,
        phone: null,
        email: null,
        websiteUrl: null,
        costDetails: null,
        eligibility: null,
        updatedAt: "2026-07-15T00:00:00Z",
        hours: {
          timezone: "America/Halifax",
          weeklyHours: [
            { dayOfWeek: "MONDAY", closed: false, opensAt: "09:00:00", closesAt: "17:00:00", overnight: false },
          ],
          hoursStatus: "OPEN",
          openNow: true,
        },
      }),
    });

    const result = await getResourceBySlug("halifax-central-library");

    expect(result.hours.hoursStatus).toBe("OPEN");
    expect(result.hours.weeklyHours).toHaveLength(1);
    expect(result.hours.weeklyHours[0]!.dayOfWeek).toBe("MONDAY");
  });

  it("rejects a malformed/missing hours shape safely (a generic recoverable error, not a crash)", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        ...resourceSummary,
        description: null,
        addressLine1: null,
        addressLine2: null,
        postalCode: null,
        phone: null,
        email: null,
        websiteUrl: null,
        costDetails: null,
        eligibility: null,
        updatedAt: "2026-07-15T00:00:00Z",
        hours: { timezone: "America/Halifax" /* missing weeklyHours/hoursStatus */ },
      }),
    });

    await expect(getResourceBySlug("halifax-central-library")).rejects.toBeInstanceOf(ApiResponseShapeError);
  });

  it("propagates a 404 as ApiRequestError so callers can call notFound()", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 404,
      json: async () => ({ status: 404, code: "RESOURCE_NOT_FOUND", message: "No resource exists with that slug." }),
    });

    const error = await getResourceBySlug("unknown-slug").catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).status).toBe(404);
  });
});

// ---- getNearbyResources (Milestone 7B, consuming Milestone 7A's GET /resources/nearby) ----

const nearbyResourceSummary = {
  ...resourceSummary,
  latitude: 44.6488,
  longitude: -63.5752,
  distanceMeters: 1204.7,
};

describe("getNearbyResources", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("requests the nearby endpoint with latitude/longitude in the correct order (not swapped)", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [nearbyResourceSummary], page: 0, size: 12, totalElements: 1, totalPages: 1 }),
    });

    await getNearbyResources({ latitude: 44.6488, longitude: -63.5752 });

    const requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.pathname).toBe("/api/v1/resources/nearby");
    expect(requestedUrl.searchParams.get("latitude")).toBe("44.6488");
    expect(requestedUrl.searchParams.get("longitude")).toBe("-63.5752");
  });

  it("defaults radiusKm/page/size when not provided", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }),
    });

    await getNearbyResources({ latitude: 44.6488, longitude: -63.5752 });

    const requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.searchParams.get("radiusKm")).toBe("5");
    expect(requestedUrl.searchParams.get("page")).toBe("0");
    expect(requestedUrl.searchParams.get("size")).toBe("12");
  });

  it("encodes a custom radiusKm", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }),
    });

    await getNearbyResources({ latitude: 44.6488, longitude: -63.5752, radiusKm: 25 });

    const requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.searchParams.get("radiusKm")).toBe("25");
  });

  it("combines q/categoryId/costType/verificationStatus/openNow exactly like getResources", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }),
    });

    await getNearbyResources({
      latitude: 44.6488,
      longitude: -63.5752,
      q: "library",
      categoryId: 3,
      costType: "FREE",
      verificationStatus: "VERIFIED",
      openNow: true,
    });

    const requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.searchParams.get("q")).toBe("library");
    expect(requestedUrl.searchParams.get("categoryId")).toBe("3");
    expect(requestedUrl.searchParams.get("costType")).toBe("FREE");
    expect(requestedUrl.searchParams.get("verificationStatus")).toBe("VERIFIED");
    expect(requestedUrl.searchParams.get("openNow")).toBe("true");
  });

  it("omits blank q and false/absent openNow, matching getResources' semantics", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }),
    });

    await getNearbyResources({ latitude: 44.6488, longitude: -63.5752, q: "", openNow: false });

    const requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.searchParams.has("q")).toBe(false);
    expect(requestedUrl.searchParams.has("openNow")).toBe(false);
  });

  it("passes the AbortSignal through to fetch", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 12, totalElements: 0, totalPages: 0 }),
    });
    const controller = new AbortController();

    await getNearbyResources({ latitude: 44.6488, longitude: -63.5752, signal: controller.signal });

    expect((global.fetch as jest.Mock).mock.calls[0][1]).toMatchObject({ signal: controller.signal });
  });

  it.each([
    ["latitude", { latitude: 91, longitude: -63.5752 }],
    ["latitude", { latitude: Number.NaN, longitude: -63.5752 }],
    ["longitude", { latitude: 44.6488, longitude: 181 }],
    ["longitude", { latitude: 44.6488, longitude: Number.POSITIVE_INFINITY }],
  ])("rejects an invalid %s before making a network request", (_field, coords) => {
    expect(() => getNearbyResources(coords)).toThrow(RangeError);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it("rejects a zero or negative radiusKm before making a network request", () => {
    expect(() => getNearbyResources({ latitude: 44.6488, longitude: -63.5752, radiusKm: 0 })).toThrow(RangeError);
    expect(() => getNearbyResources({ latitude: 44.6488, longitude: -63.5752, radiusKm: -5 })).toThrow(RangeError);
    expect(global.fetch).not.toHaveBeenCalled();
  });

  it("parses distanceMeters and coordinates on each nearby result", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [nearbyResourceSummary], page: 0, size: 12, totalElements: 1, totalPages: 1 }),
    });

    const result = await getNearbyResources({ latitude: 44.6488, longitude: -63.5752 });

    expect(result.content[0]!.distanceMeters).toBe(1204.7);
    expect(result.content[0]!.latitude).toBe(44.6488);
    expect(result.content[0]!.longitude).toBe(-63.5752);
  });

  it("rejects a response with a negative distance safely (a generic recoverable error, not a crash)", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        content: [{ ...nearbyResourceSummary, distanceMeters: -5 }],
        page: 0,
        size: 12,
        totalElements: 1,
        totalPages: 1,
      }),
    });

    await expect(getNearbyResources({ latitude: 44.6488, longitude: -63.5752 })).rejects.toBeInstanceOf(
      ApiResponseShapeError,
    );
  });

  it("rejects a response with an out-of-range coordinate safely", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({
        content: [{ ...nearbyResourceSummary, latitude: 200 }],
        page: 0,
        size: 12,
        totalElements: 1,
        totalPages: 1,
      }),
    });

    await expect(getNearbyResources({ latitude: 44.6488, longitude: -63.5752 })).rejects.toBeInstanceOf(
      ApiResponseShapeError,
    );
  });

  it("propagates a 400 as ApiRequestError (e.g. a radius above the backend's own maximum, which this client only lower-bounds)", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 400,
      json: async () => ({ status: 400, code: "INVALID_RADIUS", message: "radiusKm must be at most 50." }),
    });

    const error = await getNearbyResources({ latitude: 44.6488, longitude: -63.5752, radiusKm: 999 }).catch(
      (e: unknown) => e,
    );

    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).status).toBe(400);
  });
});
