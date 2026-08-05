import {
  createCorrectionReport,
  getCorrectionReports,
  getCorrectionReport,
  withdrawCorrectionReport,
} from "./correction-reports";
import { ApiRequestError } from "./errors";

const reportResponse = {
  id: "33333333-3333-3333-3333-333333333333",
  resource: { resourceId: "11111111-1111-1111-1111-111111111111", name: "Halifax Central Library", slug: "halifax-central-library" },
  issueType: "ADDRESS",
  explanation: "The address is out of date.",
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
};

const input = { issueType: "ADDRESS" as const, explanation: "The address is out of date." };

describe("createCorrectionReport", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("sends a POST request nested under the target resource id", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 201, json: async () => reportResponse });

    await createCorrectionReport("11111111-1111-1111-1111-111111111111", input, "token-1");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe(
      "/api/v1/resources/11111111-1111-1111-1111-111111111111/correction-reports",
    );
    expect(init.method).toBe("POST");
    expect(init.headers.Authorization).toBe("Bearer token-1");
  });

  it("never sends a user id or status in the request body", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 201, json: async () => reportResponse });

    await createCorrectionReport("11111111-1111-1111-1111-111111111111", input, "token-1");

    const [, init] = (global.fetch as jest.Mock).mock.calls[0];
    const body = JSON.parse(init.body as string);
    expect(body).not.toHaveProperty("reportedByUserId");
    expect(body).not.toHaveProperty("userId");
    expect(body).not.toHaveProperty("status");
  });

  it("propagates a 409 conflict as ApiRequestError", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 409,
      json: async () => ({ status: 409, code: "CORRECTION_REPORT_CONFLICT", message: "Already pending." }),
    });

    const error = await createCorrectionReport("r1", input, "token-1").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).status).toBe(409);
  });
});

describe("getCorrectionReports", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("requests the current-user list with pagination params", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [reportResponse], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    });

    await getCorrectionReports({ page: 2, size: 10, accessToken: "token-1" });

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    const parsed = new URL(url as string);
    expect(parsed.pathname).toBe("/api/v1/users/me/correction-reports");
    expect(parsed.searchParams.get("page")).toBe("2");
    expect(parsed.searchParams.get("size")).toBe("10");
  });
});

describe("getCorrectionReport", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("propagates a 404 as ApiRequestError", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 404,
      json: async () => ({ status: 404, code: "CORRECTION_REPORT_NOT_FOUND", message: "Not found." }),
    });

    const error = await getCorrectionReport("x", "token-1").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).status).toBe(404);
  });
});

describe("withdrawCorrectionReport", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("sends a POST request to the withdraw endpoint", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ ...reportResponse, status: "WITHDRAWN", withdrawnAt: "2026-08-04T01:00:00Z" }),
    });

    await withdrawCorrectionReport("33333333-3333-3333-3333-333333333333", "token-1");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe(
      "/api/v1/users/me/correction-reports/33333333-3333-3333-3333-333333333333/withdraw",
    );
    expect(init.method).toBe("POST");
  });
});
