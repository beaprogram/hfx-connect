import { buildResourcesHref, parseResourceListParams } from "./resource-list-params";

describe("parseResourceListParams", () => {
  it("defaults to page 0, no category, and sort=name when nothing is provided", () => {
    expect(parseResourceListParams({})).toEqual({ page: 0, categoryId: undefined, sort: "name" });
  });

  it("parses valid page, categoryId, and sort values", () => {
    expect(parseResourceListParams({ page: "2", categoryId: "5", sort: "createdAt" })).toEqual({
      page: 2,
      categoryId: 5,
      sort: "createdAt",
    });
  });

  it("falls back to page 0 for a negative page", () => {
    expect(parseResourceListParams({ page: "-3" }).page).toBe(0);
  });

  it("falls back to page 0 for a non-numeric page", () => {
    expect(parseResourceListParams({ page: "not-a-number" }).page).toBe(0);
  });

  it("falls back to sort=name for an unsupported sort value", () => {
    expect(parseResourceListParams({ sort: "priceAsc" }).sort).toBe("name");
  });

  it("drops a non-numeric or non-positive categoryId", () => {
    expect(parseResourceListParams({ categoryId: "not-a-number" }).categoryId).toBeUndefined();
    expect(parseResourceListParams({ categoryId: "-1" }).categoryId).toBeUndefined();
    expect(parseResourceListParams({ categoryId: "0" }).categoryId).toBeUndefined();
  });

  it("takes the first value when a parameter is repeated", () => {
    expect(parseResourceListParams({ page: ["2", "5"] }).page).toBe(2);
  });
});

describe("buildResourcesHref", () => {
  it("builds a bare /resources path when nothing is set", () => {
    expect(buildResourcesHref({})).toBe("/resources");
  });

  it("omits page=0 and sort=name (the defaults) but includes categoryId", () => {
    expect(buildResourcesHref({ page: 0, sort: "name", categoryId: 4 })).toBe("/resources?categoryId=4");
  });

  it("includes a non-default page and sort", () => {
    expect(buildResourcesHref({ page: 2, sort: "createdAt" })).toBe("/resources?sort=createdAt&page=2");
  });
});
