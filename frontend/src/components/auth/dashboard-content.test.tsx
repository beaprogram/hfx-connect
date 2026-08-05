import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { DashboardContent } from "./dashboard-content";
import { getCurrentUser } from "@/lib/api/auth";
import { getSavedResources } from "@/lib/api/saved-resources";
import { getResourceSubmissions } from "@/lib/api/resource-submissions";
import { getCorrectionReports } from "@/lib/api/correction-reports";
import { useAuth } from "@/lib/auth/auth-provider";

jest.mock("@/lib/api/auth");
jest.mock("@/lib/api/saved-resources");
jest.mock("@/lib/api/resource-submissions");
jest.mock("@/lib/api/correction-reports");
jest.mock("@/lib/auth/auth-provider");
const mockPush = jest.fn();
jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush, replace: jest.fn() }),
}));

const mockGetCurrentUser = getCurrentUser as jest.MockedFunction<typeof getCurrentUser>;
const mockGetSavedResources = getSavedResources as jest.MockedFunction<typeof getSavedResources>;
const mockGetResourceSubmissions = getResourceSubmissions as jest.MockedFunction<typeof getResourceSubmissions>;
const mockGetCorrectionReports = getCorrectionReports as jest.MockedFunction<typeof getCorrectionReports>;
const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;

const user = {
  id: "1",
  email: "student@example.org",
  role: "USER" as const,
  status: "ACTIVE" as const,
  emailVerified: false,
  createdAt: "2026-07-27T00:00:00Z",
};

const emptySavedPage = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 };

function renderWithQueryClient(ui: React.ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

function authenticate() {
  mockUseAuth.mockReturnValue({
    state: { status: "authenticated", user, accessToken: "token", expiresAt: Date.now() + 900_000 },
    login: jest.fn(),
    logout: jest.fn(),
    getValidAccessToken: jest.fn().mockResolvedValue("token"),
  });
}

describe("DashboardContent", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockGetSavedResources.mockResolvedValue(emptySavedPage);
    mockGetResourceSubmissions.mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
    mockGetCorrectionReports.mockResolvedValue({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 });
  });

  it("shows a loading state, then the account's safe fields once loaded", async () => {
    authenticate();
    mockGetCurrentUser.mockResolvedValue(user);

    renderWithQueryClient(<DashboardContent />);

    expect(screen.getByRole("status")).toHaveTextContent("Loading your account…");
    await waitFor(() => expect(screen.getByText("student@example.org")).toBeInTheDocument());
    expect(screen.getByText("USER")).toBeInTheDocument();
    expect(screen.getByText("ACTIVE")).toBeInTheDocument();
  });

  it("shows the real Saved Resources, My Resource Submissions, and My Correction Reports sections, but no other fabricated features", async () => {
    authenticate();
    mockGetCurrentUser.mockResolvedValue(user);

    renderWithQueryClient(<DashboardContent />);

    await waitFor(() => expect(screen.getByText("student@example.org")).toBeInTheDocument());
    expect(screen.getByRole("heading", { name: "Saved Resources" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "My Resource Submissions" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "My Correction Reports" })).toBeInTheDocument();
    expect(screen.queryByText(/moderation/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/recommend/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/recent activity/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/estimated review time/i)).not.toBeInTheDocument();
  });

  it("logs out and redirects to /login", async () => {
    const logout = jest.fn().mockResolvedValue(undefined);
    mockUseAuth.mockReturnValue({
      state: { status: "authenticated", user, accessToken: "token", expiresAt: Date.now() + 900_000 },
      login: jest.fn(),
      logout,
      getValidAccessToken: jest.fn().mockResolvedValue("token"),
    });
    mockGetCurrentUser.mockResolvedValue(user);
    const testUser = userEvent.setup();

    renderWithQueryClient(<DashboardContent />);
    await waitFor(() => expect(screen.getByText("student@example.org")).toBeInTheDocument());

    await testUser.click(screen.getByRole("button", { name: "Log out" }));

    expect(logout).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(mockPush).toHaveBeenCalledWith("/login"));
  });

  it("shows an error state when the account can't be loaded", async () => {
    authenticate();
    mockGetCurrentUser.mockRejectedValue(new Error("boom"));

    renderWithQueryClient(<DashboardContent />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/couldn't load your account/i);
  });
});
