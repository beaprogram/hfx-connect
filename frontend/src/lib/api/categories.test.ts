import { getCategories } from "./categories";

describe("getCategories", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("requests the category list with the active filter applied", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 }),
    });

    await getCategories({ active: true, size: 50 });

    const requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.pathname).toBe("/api/v1/categories");
    expect(requestedUrl.searchParams.get("active")).toBe("true");
    expect(requestedUrl.searchParams.get("size")).toBe("50");
  });

  it("omits the active filter when not provided, matching the backend's any-state default", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 }),
    });

    await getCategories();

    const requestedUrl = new URL((global.fetch as jest.Mock).mock.calls[0][0] as string);
    expect(requestedUrl.searchParams.has("active")).toBe(false);
  });
});
