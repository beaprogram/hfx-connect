import { render, screen, waitFor } from "@testing-library/react";
import { ProtectedRoute } from "./protected-route";
import { useAuth, type AuthState } from "@/lib/auth/auth-provider";

jest.mock("@/lib/auth/auth-provider", () => ({
  ...jest.requireActual("@/lib/auth/auth-provider"),
  useAuth: jest.fn(),
}));
const mockReplace = jest.fn();
jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: jest.fn(), replace: mockReplace }),
  usePathname: () => "/dashboard",
}));

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;

function mockState(state: AuthState) {
  mockUseAuth.mockReturnValue({
    state,
    login: jest.fn(),
    logout: jest.fn(),
    getValidAccessToken: jest.fn(),
  });
}

describe("ProtectedRoute", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("shows a loading state and never renders protected content while restoration is in progress", () => {
    mockState({ status: "loading" });

    render(
      <ProtectedRoute>
        <p>Secret dashboard content</p>
      </ProtectedRoute>,
    );

    expect(screen.getByRole("status")).toHaveTextContent(/checking your session/i);
    expect(screen.queryByText("Secret dashboard content")).not.toBeInTheDocument();
  });

  it("redirects to /login and never renders protected content when unauthenticated", async () => {
    mockState({ status: "unauthenticated" });

    render(
      <ProtectedRoute>
        <p>Secret dashboard content</p>
      </ProtectedRoute>,
    );

    expect(screen.queryByText("Secret dashboard content")).not.toBeInTheDocument();
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login?returnTo=%2Fdashboard"));
  });

  it("renders the protected content once authenticated", () => {
    mockState({
      status: "authenticated",
      user: { id: "1", email: "a@example.org", role: "USER", status: "ACTIVE", emailVerified: false, createdAt: "2026-07-27T00:00:00Z" },
      accessToken: "token",
      expiresAt: Date.now() + 900_000,
    });

    render(
      <ProtectedRoute>
        <p>Secret dashboard content</p>
      </ProtectedRoute>,
    );

    expect(screen.getByText("Secret dashboard content")).toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });
});
