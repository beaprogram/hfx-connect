import {
  createResourceSubmission,
  getResourceSubmissions,
  getResourceSubmission,
  withdrawResourceSubmission,
} from "./resource-submissions";
import { ApiRequestError } from "./errors";

const submissionResponse = {
  id: "22222222-2222-2222-2222-222222222222",
  category: { id: 1, name: "Food Assistance", slug: "food-assistance" },
  name: "Halifax Food Bank",
  shortDescription: "Free groceries for anyone in need.",
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
};

const input = {
  categoryId: 1,
  name: "Halifax Food Bank",
  shortDescription: "Free groceries for anyone in need.",
  addressLine1: "123 Main St",
  city: "Halifax",
  province: "NS",
  postalCode: "B3H 4R2",
};

describe("createResourceSubmission", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("sends a POST request to the current-user resource-submissions endpoint with a Bearer token and JSON body", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 201,
      json: async () => submissionResponse,
    });

    await createResourceSubmission(input, "token-1");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe("/api/v1/users/me/resource-submissions");
    expect(init.method).toBe("POST");
    expect(init.headers.Authorization).toBe("Bearer token-1");
    expect(JSON.parse(init.body as string)).toMatchObject({ name: "Halifax Food Bank", categoryId: 1 });
  });

  it("never sends a user id in the request body", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 201,
      json: async () => submissionResponse,
    });

    await createResourceSubmission(input, "token-1");

    const [, init] = (global.fetch as jest.Mock).mock.calls[0];
    const body = JSON.parse(init.body as string);
    expect(body).not.toHaveProperty("submittedByUserId");
    expect(body).not.toHaveProperty("userId");
    expect(body).not.toHaveProperty("status");
  });

  it("propagates a 409 conflict as ApiRequestError", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 409,
      json: async () => ({ status: 409, code: "RESOURCE_SUBMISSION_CONFLICT", message: "Already pending." }),
    });

    const error = await createResourceSubmission(input, "token-1").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).status).toBe(409);
  });

  it("propagates field validation errors", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 400,
      json: async () => ({
        status: 400,
        code: "VALIDATION_ERROR",
        message: "Invalid.",
        fieldErrors: { name: "Resource name is required." },
      }),
    });

    const error = (await createResourceSubmission(input, "token-1").catch((e: unknown) => e)) as ApiRequestError;
    expect(error.fieldErrors).toEqual({ name: "Resource name is required." });
  });
});

describe("getResourceSubmissions", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("requests the current-user list with pagination params", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ content: [submissionResponse], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    });

    await getResourceSubmissions({ page: 1, size: 5, sort: "status", accessToken: "token-1" });

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    const parsed = new URL(url as string);
    expect(parsed.pathname).toBe("/api/v1/users/me/resource-submissions");
    expect(parsed.searchParams.get("page")).toBe("1");
    expect(parsed.searchParams.get("size")).toBe("5");
    expect(parsed.searchParams.get("sort")).toBe("status");
    expect(init.headers.Authorization).toBe("Bearer token-1");
  });
});

describe("getResourceSubmission", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("requests one submission by id", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({ ok: true, status: 200, json: async () => submissionResponse });

    await getResourceSubmission("22222222-2222-2222-2222-222222222222", "token-1");

    const [url] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe(
      "/api/v1/users/me/resource-submissions/22222222-2222-2222-2222-222222222222",
    );
  });

  it("propagates a 404 as ApiRequestError", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: false,
      status: 404,
      json: async () => ({ status: 404, code: "RESOURCE_SUBMISSION_NOT_FOUND", message: "Not found." }),
    });

    const error = await getResourceSubmission("x", "token-1").catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiRequestError);
    expect((error as ApiRequestError).status).toBe(404);
  });
});

describe("withdrawResourceSubmission", () => {
  beforeEach(() => {
    global.fetch = jest.fn();
  });

  it("sends a POST request to the withdraw endpoint", async () => {
    (global.fetch as jest.Mock).mockResolvedValueOnce({
      ok: true,
      status: 200,
      json: async () => ({ ...submissionResponse, status: "WITHDRAWN", withdrawnAt: "2026-08-04T01:00:00Z" }),
    });

    await withdrawResourceSubmission("22222222-2222-2222-2222-222222222222", "token-1");

    const [url, init] = (global.fetch as jest.Mock).mock.calls[0];
    expect(new URL(url as string).pathname).toBe(
      "/api/v1/users/me/resource-submissions/22222222-2222-2222-2222-222222222222/withdraw",
    );
    expect(init.method).toBe("POST");
  });
});
