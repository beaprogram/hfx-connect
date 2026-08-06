import {
  getResourceSubmissionQueue,
  getResourceSubmissionForModeration,
  approveResourceSubmission,
  rejectResourceSubmission,
  getCorrectionReportQueue,
  getCorrectionReportForModeration,
  approveCorrectionReport,
  rejectCorrectionReport,
  getResourceSubmissionAuditEvents,
  getCorrectionReportAuditEvents,
  getGlobalAuditEvents,
} from "./moderation";
import { ApiRequestError } from "./errors";

const submissionQueuePage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
const submissionDetail = {
  id: "sub-1",
  category: { id: 1, name: "Food Assistance", slug: "food-assistance" },
  name: "Halifax Food Bank",
  shortDescription: "Free groceries.",
  fullDescription: null,
  addressLine1: "123 Main St",
  addressLine2: null,
  city: "Halifax",
  province: "NS",
  postalCode: "B3H 4R2",
  phone: null,
  email: null,
  websiteUrl: null,
  costType: "FREE",
  eligibilityInformation: null,
  accessibilityInformation: null,
  status: "PENDING_REVIEW",
  submittedAt: "2026-08-04T00:00:00Z",
  updatedAt: "2026-08-04T00:00:00Z",
  withdrawnAt: null,
  reviewedByUserId: null,
  reviewedAt: null,
  reviewReason: null,
  resultingResource: null,
};
const correctionQueuePage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };
const correctionDetail = {
  id: "report-1",
  resource: { resourceId: "res-1", name: "Halifax Central Library", slug: "halifax-central-library" },
  currentResource: null,
  issueType: "ADDRESS",
  explanation: "Out of date.",
  proposedName: null,
  proposedDescription: null,
  proposedAddressLine1: null,
  proposedAddressLine2: null,
  proposedCity: null,
  proposedProvince: null,
  proposedPostalCode: null,
  proposedPhone: null,
  proposedEmail: null,
  proposedWebsiteUrl: null,
  proposedCostType: null,
  proposedCostDetails: null,
  proposedEligibility: null,
  status: "PENDING_REVIEW",
  submittedAt: "2026-08-04T00:00:00Z",
  updatedAt: "2026-08-04T00:00:00Z",
  withdrawnAt: null,
  reviewedByUserId: null,
  reviewedAt: null,
  reviewReason: null,
  appliedToResourceAt: null,
};
const auditEventPage = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

beforeEach(() => {
  global.fetch = jest.fn();
});

describe("getResourceSubmissionQueue", () => {
  it("requests the moderation queue with filters and a Bearer token", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 200, json: async () => submissionQueuePage });

    await getResourceSubmissionQueue({ status: "PENDING_REVIEW", categoryId: 2, page: 1, size: 10, accessToken: "token-1" });

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    const parsed = new URL(url as string);
    expect(parsed.pathname).toBe("/api/v1/moderation/resource-submissions");
    expect(parsed.searchParams.get("status")).toBe("PENDING_REVIEW");
    expect(parsed.searchParams.get("categoryId")).toBe("2");
    expect(init.headers.Authorization).toBe("Bearer token-1");
  });

  it("propagates a 403 as ApiRequestError", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 403,
      json: async () => ({ status: 403, code: "FORBIDDEN", message: "Not allowed." }),
    });

    const error = await getResourceSubmissionQueue({ page: 0, size: 20, accessToken: "token-1" }).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).status).toBe(403);
  });
});

describe("getResourceSubmissionForModeration", () => {
  it("requests one submission by id, not owner-scoped", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 200, json: async () => submissionDetail });

    await getResourceSubmissionForModeration("sub-1", "token-1");

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe("/api/v1/moderation/resource-submissions/sub-1");
  });
});

describe("approveResourceSubmission", () => {
  it("sends only a reason in the request body — no actor or status field", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ ...submissionDetail, status: "APPROVED" }),
    });

    await approveResourceSubmission("sub-1", "Verified.", "token-1");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe("/api/v1/moderation/resource-submissions/sub-1/approve");
    expect(init.method).toBe("POST");
    const body = JSON.parse(init.body as string);
    expect(body).toEqual({ reason: "Verified." });
  });

  it("propagates a 403 SELF_REVIEW_NOT_ALLOWED as ApiRequestError", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 403,
      json: async () => ({ status: 403, code: "SELF_REVIEW_NOT_ALLOWED", message: "Cannot review your own." }),
    });

    const error = await approveResourceSubmission("sub-1", "Verified.", "token-1").catch((e: unknown) => e);
    expect((error as ApiRequestError).code).toBe("SELF_REVIEW_NOT_ALLOWED");
  });

  it("propagates a 409 CONTRIBUTION_ALREADY_REVIEWED as ApiRequestError", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 409,
      json: async () => ({ status: 409, code: "CONTRIBUTION_ALREADY_REVIEWED", message: "Already decided." }),
    });

    const error = await approveResourceSubmission("sub-1", "Verified.", "token-1").catch((e: unknown) => e);
    expect((error as ApiRequestError).status).toBe(409);
  });
});

describe("rejectResourceSubmission", () => {
  it("sends only a reason in the request body", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ ...submissionDetail, status: "REJECTED" }),
    });

    await rejectResourceSubmission("sub-1", "Not enough detail.", "token-1");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe("/api/v1/moderation/resource-submissions/sub-1/reject");
    expect(JSON.parse(init.body as string)).toEqual({ reason: "Not enough detail." });
  });
});

describe("getCorrectionReportQueue", () => {
  it("requests the moderation queue with issueType filter", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 200, json: async () => correctionQueuePage });

    await getCorrectionReportQueue({ status: "PENDING_REVIEW", issueType: "ADDRESS", page: 0, size: 20, accessToken: "token-1" });

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    const parsed = new URL(url as string);
    expect(parsed.pathname).toBe("/api/v1/moderation/correction-reports");
    expect(parsed.searchParams.get("issueType")).toBe("ADDRESS");
  });
});

describe("getCorrectionReportForModeration", () => {
  it("requests one report by id, not owner-scoped", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 200, json: async () => correctionDetail });

    await getCorrectionReportForModeration("report-1", "token-1");

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe("/api/v1/moderation/correction-reports/report-1");
  });
});

describe("approveCorrectionReport", () => {
  it("sends reason, applyProposedChanges, and deactivateResource — nothing else", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ ...correctionDetail, status: "APPROVED" }),
    });

    await approveCorrectionReport(
      "report-1",
      { reason: "Confirmed.", applyProposedChanges: true, deactivateResource: false },
      "token-1",
    );

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe("/api/v1/moderation/correction-reports/report-1/approve");
    expect(JSON.parse(init.body as string)).toEqual({
      reason: "Confirmed.",
      applyProposedChanges: true,
      deactivateResource: false,
    });
  });

  it("propagates a 400 UNSUPPORTED_CORRECTION_APPLICATION as ApiRequestError", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 400,
      json: async () => ({ status: 400, code: "UNSUPPORTED_CORRECTION_APPLICATION", message: "Not supported." }),
    });

    const error = await approveCorrectionReport(
      "report-1",
      { reason: "x", applyProposedChanges: true, deactivateResource: false },
      "token-1",
    ).catch((e: unknown) => e);
    expect((error as ApiRequestError).code).toBe("UNSUPPORTED_CORRECTION_APPLICATION");
  });

  it("propagates a 400 INVALID_DEACTIVATION_REQUEST as ApiRequestError", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 400,
      json: async () => ({ status: 400, code: "INVALID_DEACTIVATION_REQUEST", message: "Invalid." }),
    });

    const error = await approveCorrectionReport(
      "report-1",
      { reason: "x", applyProposedChanges: false, deactivateResource: true },
      "token-1",
    ).catch((e: unknown) => e);
    expect((error as ApiRequestError).code).toBe("INVALID_DEACTIVATION_REQUEST");
  });
});

describe("rejectCorrectionReport", () => {
  it("sends only a reason", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ ...correctionDetail, status: "REJECTED" }),
    });

    await rejectCorrectionReport("report-1", "Not verifiable.", "token-1");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe("/api/v1/moderation/correction-reports/report-1/reject");
    expect(JSON.parse(init.body as string)).toEqual({ reason: "Not verifiable." });
  });
});

describe("audit event endpoints", () => {
  it("requests a submission's audit history", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 200, json: async () => auditEventPage });

    await getResourceSubmissionAuditEvents("sub-1", { page: 0, size: 20, accessToken: "token-1" });

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe("/api/v1/moderation/resource-submissions/sub-1/audit-events");
  });

  it("requests a report's audit history", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 200, json: async () => auditEventPage });

    await getCorrectionReportAuditEvents("report-1", { page: 0, size: 20, accessToken: "token-1" });

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe("/api/v1/moderation/correction-reports/report-1/audit-events");
  });

  it("requests the global audit list with filters", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 200, json: async () => auditEventPage });

    await getGlobalAuditEvents({ contributionType: "RESOURCE_SUBMISSION", decision: "APPROVED", page: 0, size: 20, accessToken: "token-1" });

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    const parsed = new URL(url as string);
    expect(parsed.pathname).toBe("/api/v1/moderation/audit-events");
    expect(parsed.searchParams.get("contributionType")).toBe("RESOURCE_SUBMISSION");
    expect(parsed.searchParams.get("decision")).toBe("APPROVED");
  });
});
