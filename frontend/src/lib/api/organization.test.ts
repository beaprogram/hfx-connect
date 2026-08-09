import {
  createOrganizationProfile,
  getOrganizationProfile,
  updateOrganizationProfile,
  getPublicOrganization,
  claimResource,
  getOrganizationClaims,
  withdrawOwnershipClaim,
  getOrganizationResources,
  getAdminOrganizationQueue,
  getAdminOrganization,
  verifyOrganization,
  rejectOrganization,
  suspendOrganization,
  getAdminOwnershipClaimQueue,
  getAdminOwnershipClaim,
  approveOwnershipClaim,
  rejectOwnershipClaim,
} from "./organization";

const organizationResponse = {
  id: "org-1",
  name: "Halifax Newcomer Services",
  slug: "halifax-newcomer-services",
  description: null,
  websiteUrl: null,
  publicEmail: null,
  phone: null,
  addressLine1: null,
  city: null,
  province: null,
  postalCode: null,
  verificationStatus: "PENDING_VERIFICATION",
  verifiedAt: null,
  verificationReason: null,
  createdAt: "2026-08-01T00:00:00Z",
  updatedAt: "2026-08-01T00:00:00Z",
};

const publicOrganizationResponse = { ...organizationResponse, verificationStatus: "VERIFIED", verificationReason: undefined };
delete (publicOrganizationResponse as { verificationReason?: string }).verificationReason;

const claimResponse = {
  id: "claim-1",
  resource: { id: "res-1", name: "Halifax Food Bank", slug: "halifax-food-bank", category: { id: 1, name: "Food", slug: "food" }, active: true },
  status: "PENDING_REVIEW",
  requestedAt: "2026-08-01T00:00:00Z",
  reviewedAt: null,
  reviewReason: null,
};

const emptyPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

beforeEach(() => {
  global.fetch = jest.fn();
});

function mockOk(body: unknown) {
  (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 200, json: async () => body });
}

describe("organization self-service", () => {
  it("createOrganizationProfile POSTs to /api/v1/organizations/me with a Bearer token", async () => {
    mockOk({ ...organizationResponse });

    await createOrganizationProfile({ name: "Halifax Newcomer Services" }, "token-1");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain("/api/v1/organizations/me");
    expect(init.method).toBe("POST");
    expect(init.headers.Authorization).toBe("Bearer token-1");
    expect(JSON.parse(init.body)).toEqual({ name: "Halifax Newcomer Services" });
  });

  it("getOrganizationProfile GETs /api/v1/organizations/me", async () => {
    mockOk({ ...organizationResponse });

    await getOrganizationProfile("token-1");

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url).pathname).toBe("/api/v1/organizations/me");
  });

  it("updateOrganizationProfile sends a PATCH, never a POST or PUT", async () => {
    mockOk({ ...organizationResponse });

    await updateOrganizationProfile({ name: "Renamed" }, "token-1");

    const [, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(init.method).toBe("PATCH");
  });

  it("getPublicOrganization requests the public slug route with no access token", async () => {
    mockOk(publicOrganizationResponse);

    await getPublicOrganization("halifax-newcomer-services");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url).pathname).toBe("/api/v1/organizations/halifax-newcomer-services");
    expect(init.headers.Authorization).toBeUndefined();
  });
});

describe("resource-ownership claims (organization side)", () => {
  it("claimResource POSTs to the resource-scoped claim route", async () => {
    mockOk(claimResponse);

    await claimResource("res-1", "token-1");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain("/api/v1/organizations/me/resource-claims/res-1");
    expect(init.method).toBe("POST");
  });

  it("getOrganizationClaims sends the status filter as a query parameter", async () => {
    mockOk(emptyPage);

    await getOrganizationClaims({ status: "PENDING_REVIEW", accessToken: "token-1" });

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url).searchParams.get("status")).toBe("PENDING_REVIEW");
  });

  it("withdrawOwnershipClaim POSTs to the withdraw route", async () => {
    mockOk({ ...claimResponse, status: "WITHDRAWN" });

    await withdrawOwnershipClaim("claim-1", "token-1");

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain("/api/v1/organizations/me/resource-claims/claim-1/withdraw");
  });

  it("getOrganizationResources GETs /api/v1/organizations/me/resources", async () => {
    mockOk(emptyPage);

    await getOrganizationResources({ accessToken: "token-1" });

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url).pathname).toBe("/api/v1/organizations/me/resources");
  });
});

describe("admin organization verification", () => {
  it("getAdminOrganizationQueue GETs /api/v1/admin/organizations", async () => {
    mockOk(emptyPage);

    await getAdminOrganizationQueue({ accessToken: "token-1" });

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url).pathname).toBe("/api/v1/admin/organizations");
  });

  it("getAdminOrganization GETs the organization by id", async () => {
    mockOk({ ...organizationResponse, ownerUserId: "owner-1", verifiedByUserId: null });

    await getAdminOrganization("org-1", "token-1");

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain("/api/v1/admin/organizations/org-1");
  });

  it.each([
    ["verifyOrganization", verifyOrganization, "verify"],
    ["rejectOrganization", rejectOrganization, "reject"],
    ["suspendOrganization", suspendOrganization, "suspend"],
  ] as const)("%s POSTs the reason to the %s route", async (_name, action, segment) => {
    mockOk({ ...organizationResponse, ownerUserId: "owner-1", verifiedByUserId: null });

    await action("org-1", "Confirmed against public registry.", "token-1");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain(`/api/v1/admin/organizations/org-1/${segment}`);
    expect(JSON.parse(init.body)).toEqual({ reason: "Confirmed against public registry." });
  });
});

describe("admin resource-ownership-claim review", () => {
  it("getAdminOwnershipClaimQueue GETs /api/v1/admin/resource-ownership-claims", async () => {
    mockOk(emptyPage);

    await getAdminOwnershipClaimQueue({ accessToken: "token-1" });

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url).pathname).toBe("/api/v1/admin/resource-ownership-claims");
  });

  it("getAdminOwnershipClaim GETs the claim by id", async () => {
    mockOk({
      id: "claim-1",
      organizationId: "org-1",
      organizationName: "Halifax Newcomer Services",
      organizationSlug: "halifax-newcomer-services",
      organizationVerificationStatus: "VERIFIED",
      resource: claimResponse.resource,
      currentResourceOrganizationId: null,
      status: "PENDING_REVIEW",
      requestedAt: "2026-08-01T00:00:00Z",
      reviewedByUserId: null,
      reviewedAt: null,
      reviewReason: null,
    });

    await getAdminOwnershipClaim("claim-1", "token-1");

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain("/api/v1/admin/resource-ownership-claims/claim-1");
  });

  it.each([
    ["approveOwnershipClaim", approveOwnershipClaim, "approve"],
    ["rejectOwnershipClaim", rejectOwnershipClaim, "reject"],
  ] as const)("%s POSTs the reason to the %s route", async (_name, action, segment) => {
    mockOk({
      id: "claim-1",
      organizationId: "org-1",
      organizationName: "Halifax Newcomer Services",
      organizationSlug: "halifax-newcomer-services",
      organizationVerificationStatus: "VERIFIED",
      resource: claimResponse.resource,
      currentResourceOrganizationId: null,
      status: "APPROVED",
      requestedAt: "2026-08-01T00:00:00Z",
      reviewedByUserId: "admin-1",
      reviewedAt: "2026-08-02T00:00:00Z",
      reviewReason: "Confirmed.",
    });

    await action("claim-1", "Confirmed.", "token-1");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(url).toContain(`/api/v1/admin/resource-ownership-claims/claim-1/${segment}`);
    expect(JSON.parse(init.body)).toEqual({ reason: "Confirmed." });
  });
});
