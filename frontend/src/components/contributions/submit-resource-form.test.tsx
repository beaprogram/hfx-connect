import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SubmitResourceForm } from "./submit-resource-form";
import { getCategories } from "@/lib/api/categories";
import { createResourceSubmission } from "@/lib/api/resource-submissions";
import { useAuth } from "@/lib/auth/auth-provider";
import { ApiRequestError } from "@/lib/api/errors";

jest.mock("@/lib/api/categories");
jest.mock("@/lib/api/resource-submissions");
jest.mock("@/lib/auth/auth-provider");

const mockGetCategories = getCategories as jest.MockedFunction<typeof getCategories>;
const mockCreateResourceSubmission = createResourceSubmission as jest.MockedFunction<typeof createResourceSubmission>;
const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;

const submissionResponse = {
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
  costType: "UNKNOWN" as const,
  eligibilityInformation: null,
  accessibilityInformation: null,
  status: "PENDING_REVIEW" as const,
  submittedAt: "2026-08-04T00:00:00Z",
  updatedAt: "2026-08-04T00:00:00Z",
  withdrawnAt: null,
};

function renderWithQueryClient() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <SubmitResourceForm />
    </QueryClientProvider>,
  );
}

async function fillRequiredFields(user: ReturnType<typeof userEvent.setup>) {
  await user.selectOptions(screen.getByLabelText("Category"), "1");
  await user.type(screen.getByLabelText("Resource name"), "Halifax Food Bank");
  await user.type(screen.getByLabelText("Short description"), "Free groceries.");
  await user.type(screen.getByLabelText("Address line 1"), "123 Main St");
  await user.type(screen.getByLabelText("City"), "Halifax");
  await user.type(screen.getByLabelText("Province"), "NS");
  await user.type(screen.getByLabelText("Postal code"), "B3H 4R2");
}

describe("SubmitResourceForm", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockUseAuth.mockReturnValue({
      state: {
        status: "authenticated",
        user: { id: "user-1", email: "a@example.org", role: "USER", status: "ACTIVE", emailVerified: false, createdAt: "2026-07-15T00:00:00Z" },
        accessToken: "token-1",
        expiresAt: Date.now() + 900_000,
      },
      login: jest.fn(),
      logout: jest.fn(),
      getValidAccessToken: jest.fn().mockResolvedValue("token-1"),
    });
    mockGetCategories.mockResolvedValue({
      content: [{ id: 1, name: "Food Assistance", slug: "food-assistance", description: null, active: true, createdAt: "", updatedAt: "" }],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
  });

  it("loads categories into the select", async () => {
    renderWithQueryClient();

    expect(await screen.findByRole("option", { name: "Food Assistance" })).toBeInTheDocument();
  });

  it("has accessible labelled fields", async () => {
    renderWithQueryClient();
    await screen.findByRole("option", { name: "Food Assistance" });

    expect(screen.getByLabelText("Category")).toBeInTheDocument();
    expect(screen.getByLabelText("Resource name")).toBeInTheDocument();
    expect(screen.getByLabelText("Short description")).toBeInTheDocument();
    expect(screen.getByLabelText("Address line 1")).toBeInTheDocument();
  });

  it("does not render any privileged fields (role, verification, active, status, location)", async () => {
    renderWithQueryClient();
    await screen.findByRole("option", { name: "Food Assistance" });

    expect(screen.queryByLabelText(/verification/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^active$/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/^status$/i)).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/latitude|longitude/i)).not.toBeInTheDocument();
  });

  it("submits and shows a review-required success message, never a publish claim", async () => {
    mockCreateResourceSubmission.mockResolvedValue(submissionResponse);
    const user = userEvent.setup();
    renderWithQueryClient();
    await screen.findByRole("option", { name: "Food Assistance" });

    await fillRequiredFields(user);
    await user.click(screen.getByRole("button", { name: "Submit for review" }));

    expect(await screen.findByText(/sent for review/i)).toBeInTheDocument();
    expect(screen.queryByText(/is now live/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/is now public/i)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View your submission" })).toHaveAttribute("href", "/dashboard/submissions/sub-1");
  });

  it("maps server field-validation errors accessibly", async () => {
    mockCreateResourceSubmission.mockRejectedValue(
      new ApiRequestError("Invalid.", 400, "VALIDATION_ERROR", { name: "Resource name is required." }),
    );
    const user = userEvent.setup();
    renderWithQueryClient();
    await screen.findByRole("option", { name: "Food Assistance" });

    await fillRequiredFields(user);
    await user.click(screen.getByRole("button", { name: "Submit for review" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/fix the highlighted fields/i);
    expect(screen.getByText("Resource name is required.")).toBeInTheDocument();
  });

  it("shows a conflict message for a duplicate pending submission", async () => {
    mockCreateResourceSubmission.mockRejectedValue(new ApiRequestError("Conflict.", 409, "RESOURCE_SUBMISSION_CONFLICT"));
    const user = userEvent.setup();
    renderWithQueryClient();
    await screen.findByRole("option", { name: "Food Assistance" });

    await fillRequiredFields(user);
    await user.click(screen.getByRole("button", { name: "Submit for review" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/already have a pending submission/i);
  });

  it("prevents a duplicate submit while a request is in flight", async () => {
    let resolveCreate: () => void = () => {};
    mockCreateResourceSubmission.mockImplementation(
      () => new Promise((resolve) => (resolveCreate = () => resolve(submissionResponse))),
    );
    const user = userEvent.setup();
    renderWithQueryClient();
    await screen.findByRole("option", { name: "Food Assistance" });

    await fillRequiredFields(user);
    const submitButton = screen.getByRole("button", { name: "Submit for review" });
    await user.click(submitButton);

    expect(screen.getByRole("button", { name: "Submitting…" })).toBeDisabled();
    resolveCreate();
    await waitFor(() => expect(mockCreateResourceSubmission).toHaveBeenCalledTimes(1));
  });
});
