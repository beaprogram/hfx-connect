import { act, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { AuthProvider, useAuth } from "./auth-provider";
import { login, logout, refreshSession } from "@/lib/api/auth";
import { ApiRequestError } from "@/lib/api/errors";

jest.mock("@/lib/api/auth");

// AuthProvider reads useQueryClient() (Milestone 8A — to clear the private
// saved-resource cache on logout/account switch), so every render needs a
// real QueryClientProvider ancestor, the same as any other TanStack-Query-
// consuming component in this codebase.
function renderWithQueryClient(ui: ReactElement, queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  return { ...render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>), queryClient };
}

const mockRefreshSession = refreshSession as jest.MockedFunction<typeof refreshSession>;
const mockLogin = login as jest.MockedFunction<typeof login>;
const mockLogout = logout as jest.MockedFunction<typeof logout>;

const user = {
  id: "11111111-1111-1111-1111-111111111111",
  email: "student@example.org",
  role: "USER" as const,
  status: "ACTIVE" as const,
  emailVerified: false,
  createdAt: "2026-07-27T00:00:00Z",
};

const sessionResult = { accessToken: "token-1", tokenType: "Bearer", expiresIn: 900, user };

function Probe() {
  const { state, getValidAccessToken } = useAuth();
  return (
    <div>
      <span data-testid="status">{state.status}</span>
      {state.status === "authenticated" && <span data-testid="email">{state.user.email}</span>}
      <button onClick={() => void getValidAccessToken()}>get-token</button>
    </div>
  );
}

describe("AuthProvider", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("starts in the loading state before restoration resolves", () => {
    mockRefreshSession.mockReturnValue(new Promise(() => {})); // never resolves within this test

    renderWithQueryClient(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    expect(screen.getByTestId("status")).toHaveTextContent("loading");
  });

  it("becomes authenticated when session restoration succeeds", async () => {
    mockRefreshSession.mockResolvedValueOnce(sessionResult);

    renderWithQueryClient(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
    expect(screen.getByTestId("email")).toHaveTextContent("student@example.org");
  });

  it("becomes unauthenticated (not an error) when there is no valid refresh cookie", async () => {
    mockRefreshSession.mockRejectedValueOnce(new ApiRequestError("Authentication is required.", 401, "AUTHENTICATION_REQUIRED"));

    renderWithQueryClient(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"));
  });

  it("does not write the access token to localStorage or sessionStorage at any point", async () => {
    const localSetItem = jest.spyOn(Storage.prototype, "setItem");
    mockRefreshSession.mockResolvedValueOnce(sessionResult);

    renderWithQueryClient(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
    expect(localSetItem).not.toHaveBeenCalled();

    localSetItem.mockRestore();
  });

  it("login() transitions directly to authenticated with the returned session", async () => {
    mockRefreshSession.mockRejectedValueOnce(new ApiRequestError("none", 401, "AUTHENTICATION_REQUIRED"));
    mockLogin.mockResolvedValueOnce(sessionResult);

    function LoginProbe() {
      const { state, login: doLogin } = useAuth();
      return (
        <div>
          <span data-testid="status">{state.status}</span>
          <button onClick={() => void doLogin("student@example.org", "correcthorsebattery")}>login</button>
        </div>
      );
    }

    const testUser = userEvent.setup();
    renderWithQueryClient(
      <AuthProvider>
        <LoginProbe />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"));
    await testUser.click(screen.getByRole("button", { name: "login" }));

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
    expect(mockLogin).toHaveBeenCalledWith({ email: "student@example.org", password: "correcthorsebattery" });
  });

  it("logout() always clears state, even if the backend call fails", async () => {
    mockRefreshSession.mockResolvedValueOnce(sessionResult);
    mockLogout.mockRejectedValueOnce(new Error("network down"));

    function LogoutProbe() {
      const { state, logout: doLogout } = useAuth();
      return (
        <div>
          <span data-testid="status">{state.status}</span>
          <button onClick={() => void doLogout()}>logout</button>
        </div>
      );
    }

    const testUser = userEvent.setup();
    renderWithQueryClient(
      <AuthProvider>
        <LogoutProbe />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
    await testUser.click(screen.getByRole("button", { name: "logout" }));

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"));
    expect(mockLogout).toHaveBeenCalledTimes(1);
  });

  it("logout() removes every private-data query (saved resources, resource submissions, correction reports) from the TanStack Query cache", async () => {
    mockRefreshSession.mockResolvedValueOnce(sessionResult);
    mockLogout.mockResolvedValueOnce(undefined);

    function LogoutProbe() {
      const { state, logout: doLogout } = useAuth();
      return (
        <div>
          <span data-testid="status">{state.status}</span>
          <button onClick={() => void doLogout()}>logout</button>
        </div>
      );
    }

    const testUser = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    renderWithQueryClient(
      <AuthProvider>
        <LogoutProbe />
      </AuthProvider>,
      queryClient,
    );
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));

    // Simulates each kind of private-data query already cached for this account.
    const privateKeys = [
      ["saved-resources", user.id, "list", { page: 0 }],
      ["resource-submissions", user.id, "list", { page: 0 }],
      ["correction-reports", user.id, "list", { page: 0 }],
      ["moderation", "resource-submissions", "queue", { page: 0 }],
    ];
    for (const key of privateKeys) {
      queryClient.setQueryData(key, { content: ["fake"] });
      expect(queryClient.getQueryData(key)).toBeDefined();
    }

    await testUser.click(screen.getByRole("button", { name: "logout" }));

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"));
    for (const key of privateKeys) {
      expect(queryClient.getQueryData(key)).toBeUndefined();
    }
  });

  it("logging in as a different account clears the previous account's private-data caches", async () => {
    const otherUser = { ...user, id: "22222222-2222-2222-2222-222222222222", email: "other@example.org" };
    mockRefreshSession.mockResolvedValueOnce(sessionResult);
    mockLogout.mockResolvedValueOnce(undefined);
    mockLogin.mockResolvedValueOnce({ ...sessionResult, user: otherUser });

    function SwitchProbe() {
      const { state, logout: doLogout, login: doLogin } = useAuth();
      return (
        <div>
          <span data-testid="status">{state.status}</span>
          {state.status === "authenticated" && <span data-testid="email">{state.user.email}</span>}
          <button onClick={() => void doLogout()}>logout</button>
          <button onClick={() => void doLogin("other@example.org", "correcthorsebattery")}>login-other</button>
        </div>
      );
    }

    const testUser = userEvent.setup();
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    renderWithQueryClient(
      <AuthProvider>
        <SwitchProbe />
      </AuthProvider>,
      queryClient,
    );
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));

    const privateKeys = [
      ["saved-resources", user.id, "list", { page: 0 }],
      ["resource-submissions", user.id, "list", { page: 0 }],
      ["correction-reports", user.id, "list", { page: 0 }],
      ["moderation", "resource-submissions", "queue", { page: 0 }],
    ];
    for (const key of privateKeys) {
      queryClient.setQueryData(key, { content: ["fake"] });
    }

    await testUser.click(screen.getByRole("button", { name: "logout" }));
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"));
    await testUser.click(screen.getByRole("button", { name: "login-other" }));

    await waitFor(() => expect(screen.getByTestId("email")).toHaveTextContent("other@example.org"));
    for (const key of privateKeys) {
      expect(queryClient.getQueryData(key)).toBeUndefined();
    }
  });

  it("coalesces concurrent refresh attempts into a single request (single-flight)", async () => {
    let resolveRefresh: (value: typeof sessionResult) => void = () => {};
    mockRefreshSession.mockImplementationOnce(
      () =>
        new Promise((resolve) => {
          resolveRefresh = resolve;
        }),
    );

    const testUser = userEvent.setup();
    renderWithQueryClient(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    // Two more callers ask for a token while the initial mount refresh is
    // still in flight — this must not trigger additional refresh calls.
    await testUser.click(screen.getByRole("button", { name: "get-token" }));
    await testUser.click(screen.getByRole("button", { name: "get-token" }));

    expect(mockRefreshSession).toHaveBeenCalledTimes(1);

    await act(async () => {
      resolveRefresh(sessionResult);
    });

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
  });

  it("does not retry indefinitely after a failed restoration", async () => {
    mockRefreshSession.mockRejectedValueOnce(new ApiRequestError("none", 401, "AUTHENTICATION_REQUIRED"));

    renderWithQueryClient(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("unauthenticated"));

    // Give any stray timers/microtasks a chance to fire, then confirm no
    // further automatic refresh attempts happened.
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(mockRefreshSession).toHaveBeenCalledTimes(1);
  });
});
