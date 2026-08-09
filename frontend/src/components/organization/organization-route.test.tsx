import { render, screen, waitFor } from "@testing-library/react";
import { OrganizationRoute } from "./organization-route";
import { useAuth, type AuthState } from "@/lib/auth/auth-provider";

jest.mock("@/lib/auth/auth-provider", () => ({
  ...jest.requireActual("@/lib/auth/auth-provider"),
  useAuth: jest.fn(),
}));
const mockReplace = jest.fn();
jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: jest.fn(), replace: mockReplace }),
  usePathname: () => "/organization",
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

describe("OrganizationRoute", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("shows a loading state and never renders organization content while restoration is in progress", () => {
    mockState({ status: "loading" });

    render(
      <OrganizationRoute>
        <p>Secret organization content</p>
      </OrganizationRoute>,
    );

    expect(screen.getByRole("status")).toHaveTextContent(/checking your session/i);
    expect(screen.queryByText("Secret organization content")).not.toBeInTheDocument();
  });

  it("redirects to /login when signed out", async () => {
    mockState({ status: "unauthenticated" });

    render(
      <OrganizationRoute>
        <p>Secret organization content</p>
      </OrganizationRoute>,
    );

    expect(screen.queryByText("Secret organization content")).not.toBeInTheDocument();
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login?returnTo=%2Forganization"));
  });

  it.each(["USER", "MODERATOR", "ADMIN"] as const)("shows access denied (not a redirect) for a signed-in %s account", (role) => {
    mockState({ status: "authenticated", user: userWithRole(role), accessToken: "token", expiresAt: Date.now() + 900_000 });

    render(
      <OrganizationRoute>
        <p>Secret organization content</p>
      </OrganizationRoute>,
    );

    expect(screen.getByRole("alert")).toHaveTextContent(/access denied/i);
    expect(screen.queryByText("Secret organization content")).not.toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it("renders organization content for an ORGANIZATION account", () => {
    mockState({ status: "authenticated", user: userWithRole("ORGANIZATION"), accessToken: "token", expiresAt: Date.now() + 900_000 });

    render(
      <OrganizationRoute>
        <p>Secret organization content</p>
      </OrganizationRoute>,
    );

    expect(screen.getByText("Secret organization content")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
