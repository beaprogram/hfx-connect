import { render, screen } from "@testing-library/react";
import { AuthNav } from "./auth-nav";
import { useAuth, type AuthState } from "@/lib/auth/auth-provider";

jest.mock("@/lib/auth/auth-provider", () => ({
  ...jest.requireActual("@/lib/auth/auth-provider"),
  useAuth: jest.fn(),
}));
jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: jest.fn(), replace: jest.fn() }),
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
  return { id: "1", email: "a@example.org", role, status: "ACTIVE" as const, emailVerified: true, createdAt: "2026-07-15T00:00:00Z" };
}

describe("AuthNav", () => {
  beforeEach(() => jest.clearAllMocks());

  it.each(["USER", "ORGANIZATION"] as const)("does not show a Moderation link for a %s account", (role) => {
    mockState({ status: "authenticated", user: userWithRole(role), accessToken: "token", expiresAt: Date.now() + 900_000 });
    render(<AuthNav />);

    expect(screen.queryByRole("link", { name: "Moderation" })).not.toBeInTheDocument();
  });

  it.each(["MODERATOR", "ADMIN"] as const)("shows a Moderation link for a %s account", (role) => {
    mockState({ status: "authenticated", user: userWithRole(role), accessToken: "token", expiresAt: Date.now() + 900_000 });
    render(<AuthNav />);

    expect(screen.getByRole("link", { name: "Moderation" })).toHaveAttribute("href", "/moderation");
  });

  it("does not show a Moderation link while signed out", () => {
    mockState({ status: "unauthenticated" });
    render(<AuthNav />);

    expect(screen.queryByRole("link", { name: "Moderation" })).not.toBeInTheDocument();
  });
});
