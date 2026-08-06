import { render, screen, waitFor } from "@testing-library/react";
import { ModerationRoute } from "./moderation-route";
import { useAuth, type AuthState } from "@/lib/auth/auth-provider";

jest.mock("@/lib/auth/auth-provider", () => ({
  ...jest.requireActual("@/lib/auth/auth-provider"),
  useAuth: jest.fn(),
}));
const mockReplace = jest.fn();
jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: jest.fn(), replace: mockReplace }),
  usePathname: () => "/moderation",
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

describe("ModerationRoute", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("shows a loading state and never renders moderation content while restoration is in progress", () => {
    mockState({ status: "loading" });

    render(
      <ModerationRoute>
        <p>Secret moderation content</p>
      </ModerationRoute>,
    );

    expect(screen.getByRole("status")).toHaveTextContent(/checking your session/i);
    expect(screen.queryByText("Secret moderation content")).not.toBeInTheDocument();
  });

  it("redirects to /login when signed out", async () => {
    mockState({ status: "unauthenticated" });

    render(
      <ModerationRoute>
        <p>Secret moderation content</p>
      </ModerationRoute>,
    );

    expect(screen.queryByText("Secret moderation content")).not.toBeInTheDocument();
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith("/login?returnTo=%2Fmoderation"));
  });

  it("shows access denied (not a redirect) for a signed-in USER account", () => {
    mockState({ status: "authenticated", user: userWithRole("USER"), accessToken: "token", expiresAt: Date.now() + 900_000 });

    render(
      <ModerationRoute>
        <p>Secret moderation content</p>
      </ModerationRoute>,
    );

    expect(screen.getByRole("alert")).toHaveTextContent(/access denied/i);
    expect(screen.queryByText("Secret moderation content")).not.toBeInTheDocument();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it("shows access denied for a signed-in ORGANIZATION account", () => {
    mockState({ status: "authenticated", user: userWithRole("ORGANIZATION"), accessToken: "token", expiresAt: Date.now() + 900_000 });

    render(
      <ModerationRoute>
        <p>Secret moderation content</p>
      </ModerationRoute>,
    );

    expect(screen.getByRole("alert")).toHaveTextContent(/access denied/i);
    expect(screen.queryByText("Secret moderation content")).not.toBeInTheDocument();
  });

  it("renders moderation content for a MODERATOR account", () => {
    mockState({ status: "authenticated", user: userWithRole("MODERATOR"), accessToken: "token", expiresAt: Date.now() + 900_000 });

    render(
      <ModerationRoute>
        <p>Secret moderation content</p>
      </ModerationRoute>,
    );

    expect(screen.getByText("Secret moderation content")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("renders moderation content for an ADMIN account", () => {
    mockState({ status: "authenticated", user: userWithRole("ADMIN"), accessToken: "token", expiresAt: Date.now() + 900_000 });

    render(
      <ModerationRoute>
        <p>Secret moderation content</p>
      </ModerationRoute>,
    );

    expect(screen.getByText("Secret moderation content")).toBeInTheDocument();
  });
});
