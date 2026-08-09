import { render, screen, waitFor } from "@testing-library/react";
import { AdminRoute } from "./admin-route";
import { useAuth, type AuthState } from "@/lib/auth/auth-provider";

jest.mock("@/lib/auth/auth-provider", () => ({
  ...jest.requireActual("@/lib/auth/auth-provider"),
  useAuth: jest.fn(),
}));
const mockReplace = jest.fn();
jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: jest.fn(), replace: mockReplace }),
  usePathname: () => "/admin/organizations",
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

function userWithRole(role: "USER" | "ORGANIZATION" | "MODERATOR" | "ADMIN") {
  return { id: "1", email: "a@example.org", role, status: "ACTIVE" as const, emailVerified: false, createdAt: "2026-07-27T00:00:00Z" };
}

describe("AdminRoute", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("shows a loading state and never renders admin content while restoration is in progress", () => {
    mockState({ status: "loading" });

    render(
      <AdminRoute>
        <p>Secret admin content</p>
      </AdminRoute>,
    );

    expect(screen.getByRole("status")).toHaveTextContent(/checking your session/i);
    expect(screen.queryByText("Secret admin content")).not.toBeInTheDocument();
  });

  it("redirects to /login when signed out", async () => {
    mockState({ status: "unauthenticated" });

    render(
      <AdminRoute>
        <p>Secret admin content</p>
      </AdminRoute>,
    );

    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login?returnTo=%2Fadmin%2Forganizations"));
  });

  it.each(["USER", "ORGANIZATION", "MODERATOR"] as const)(
    "shows access denied (not a redirect) for a signed-in %s account — organization verification is ADMIN-only",
    (role) => {
      mockState({ status: "authenticated", user: userWithRole(role), accessToken: "token", expiresAt: Date.now() + 900_000 });

      render(
        <AdminRoute>
          <p>Secret admin content</p>
        </AdminRoute>,
      );

      expect(screen.getByRole("alert")).toHaveTextContent(/access denied/i);
      expect(screen.queryByText("Secret admin content")).not.toBeInTheDocument();
      expect(mockReplace).not.toHaveBeenCalled();
    },
  );

  it("renders admin content for an ADMIN account", () => {
    mockState({ status: "authenticated", user: userWithRole("ADMIN"), accessToken: "token", expiresAt: Date.now() + 900_000 });

    render(
      <AdminRoute>
        <p>Secret admin content</p>
      </AdminRoute>,
    );

    expect(screen.getByText("Secret admin content")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
