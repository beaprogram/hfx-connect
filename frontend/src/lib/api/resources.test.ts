import { getResources, getResourceBySlug } from "./resources";
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
      }),
    });

    await getResourceBySlug("a slug/with special?chars");

    const requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.pathname).toBe("/api/v1/resources/slug/a%20slug%2Fwith%20special%3Fchars");
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
