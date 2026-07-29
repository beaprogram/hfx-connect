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

  it("trims and collapses whitespace in q", () => {
    expect(parseResourceListParams({ q: "  food   bank  " }).q).toBe("food bank");
  });

  it("normalizes a blank or whitespace-only q to undefined", () => {
    expect(parseResourceListParams({ q: "" }).q).toBeUndefined();
    expect(parseResourceListParams({ q: "   " }).q).toBeUndefined();
  });

  it("omits q entirely when not provided", () => {
    expect(parseResourceListParams({}).q).toBeUndefined();
  });

  it("truncates an over-length q rather than throwing", () => {
    const tooLong = "a".repeat(150);
    const result = parseResourceListParams({ q: tooLong });
    expect(result.q).toHaveLength(100);
  });

  it("takes the first value when q is repeated", () => {
    expect(parseResourceListParams({ q: ["first", "second"] }).q).toBe("first");
  });

  // ---- Cost/verification/openNow filters (Milestone 6B — see ADR-011) ----

  it("parses valid costType and verificationStatus values", () => {
    const result = parseResourceListParams({ costType: "FREE", verificationStatus: "VERIFIED" });
    expect(result.costType).toBe("FREE");
    expect(result.verificationStatus).toBe("VERIFIED");
  });

  it("falls back to undefined for an invalid costType or verificationStatus", () => {
    expect(parseResourceListParams({ costType: "NOT_REAL" }).costType).toBeUndefined();
    expect(parseResourceListParams({ verificationStatus: "NOT_REAL" }).verificationStatus).toBeUndefined();
  });

  it("only treats openNow=true (exact) as true; anything else is undefined", () => {
    expect(parseResourceListParams({ openNow: "true" }).openNow).toBe(true);
    expect(parseResourceListParams({ openNow: "false" }).openNow).toBeUndefined();
    expect(parseResourceListParams({ openNow: "maybe" }).openNow).toBeUndefined();
    expect(parseResourceListParams({}).openNow).toBeUndefined();
  });

  it("takes the first value when costType/verificationStatus/openNow are repeated", () => {
    expect(parseResourceListParams({ costType: ["FREE", "PAID"] }).costType).toBe("FREE");
    expect(parseResourceListParams({ openNow: ["true", "false"] }).openNow).toBe(true);
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

  it("includes q when set and omits it when blank", () => {
    expect(buildResourcesHref({ q: "food bank" })).toBe("/resources?q=food+bank");
    expect(buildResourcesHref({ q: "" })).toBe("/resources");
  });

  it("combines q with categoryId and sort", () => {
    expect(buildResourcesHref({ q: "library", categoryId: 4, sort: "createdAt" })).toBe(
      "/resources?categoryId=4&sort=createdAt&q=library",
    );
  });

  it("includes costType and verificationStatus when set", () => {
    expect(buildResourcesHref({ costType: "FREE", verificationStatus: "VERIFIED" })).toBe(
      "/resources?costType=FREE&verificationStatus=VERIFIED",
    );
  });

  it("includes openNow only when true; omits it entirely when false or absent", () => {
    expect(buildResourcesHref({ openNow: true })).toBe("/resources?openNow=true");
    expect(buildResourcesHref({ openNow: false })).toBe("/resources");
    expect(buildResourcesHref({})).toBe("/resources");
  });
});
