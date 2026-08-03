import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { SaveResourceButton } from "./save-resource-button";
import { useAuth } from "@/lib/auth/auth-provider";
import { saveResource, removeSavedResource } from "@/lib/api/saved-resources";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/saved-resources");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockSaveResource = saveResource as jest.MockedFunction<typeof saveResource>;
const mockRemoveSavedResource = removeSavedResource as jest.MockedFunction<typeof removeSavedResource>;

function renderWithQueryClient(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

function mockUnauthenticated() {
  mockUseAuth.mockReturnValue({
    state: { status: "unauthenticated" },
    login: jest.fn(),
    logout: jest.fn(),
    getValidAccessToken: jest.fn(),
  });
}

function mockAuthenticated() {
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
}

function mockLoading() {
  mockUseAuth.mockReturnValue({
    state: { status: "loading" },
    login: jest.fn(),
    logout: jest.fn(),
    getValidAccessToken: jest.fn(),
  });
}

describe("SaveResourceButton", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("renders nothing while authentication is still restoring (no flash of the wrong state)", () => {
    mockLoading();
    const { container } = renderWithQueryClient(
      <SaveResourceButton resourceId="r1" resourceName="Halifax Central Library" isSaved={false} />,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("shows a 'Sign in to save' link when signed out, naming the resource", async () => {
    mockUnauthenticated();
    renderWithQueryClient(<SaveResourceButton resourceId="r1" resourceName="Halifax Central Library" isSaved={false} />);

    const link = await screen.findByRole("link", { name: /sign in to save halifax central library/i });
    expect(link).toHaveAttribute("href", expect.stringContaining("/login"));
  });

  it("shows a labelled Save button when signed in and not saved, using aria-pressed for state", () => {
    mockAuthenticated();
    renderWithQueryClient(<SaveResourceButton resourceId="r1" resourceName="Halifax Central Library" isSaved={false} />);

    const button = screen.getByRole("button", { name: "Save Halifax Central Library" });
    expect(button).toHaveAttribute("aria-pressed", "false");
  });

  it("shows a labelled Remove button when signed in and saved", () => {
    mockAuthenticated();
    renderWithQueryClient(<SaveResourceButton resourceId="r1" resourceName="Halifax Central Library" isSaved />);

    const button = screen.getByRole("button", { name: "Remove Halifax Central Library from saved resources" });
    expect(button).toHaveAttribute("aria-pressed", "true");
  });

  it("treats an unknown (still-loading) status as not-saved without erroring", () => {
    mockAuthenticated();
    renderWithQueryClient(<SaveResourceButton resourceId="r1" resourceName="Halifax Central Library" isSaved={undefined} />);

    expect(screen.getByRole("button", { name: "Save Halifax Central Library" })).toBeInTheDocument();
  });

  it("clicking Save calls the save API and shows a loading label meanwhile", async () => {
    mockAuthenticated();
    let resolveSave: () => void = () => {};
    mockSaveResource.mockImplementation(() => new Promise((resolve) => (resolveSave = () => resolve(undefined))));
    const user = userEvent.setup();
    renderWithQueryClient(<SaveResourceButton resourceId="r1" resourceName="Halifax Central Library" isSaved={false} />);

    await user.click(screen.getByRole("button", { name: "Save Halifax Central Library" }));

    expect(screen.getByRole("button")).toBeDisabled();
    expect(screen.getByRole("button")).toHaveTextContent("Saving…");
    resolveSave();
    await waitFor(() => expect(mockSaveResource).toHaveBeenCalledWith("r1", "token-1"));
  });

  it("clicking Remove calls the remove API", async () => {
    mockAuthenticated();
    mockRemoveSavedResource.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderWithQueryClient(<SaveResourceButton resourceId="r1" resourceName="Halifax Central Library" isSaved />);

    await user.click(screen.getByRole("button", { name: "Remove Halifax Central Library from saved resources" }));

    await waitFor(() => expect(mockRemoveSavedResource).toHaveBeenCalledWith("r1", "token-1"));
  });

  it("shows a recoverable error message when the save fails", async () => {
    mockAuthenticated();
    mockSaveResource.mockRejectedValue(new Error("network down"));
    const user = userEvent.setup();
    renderWithQueryClient(<SaveResourceButton resourceId="r1" resourceName="Halifax Central Library" isSaved={false} />);

    await user.click(screen.getByRole("button", { name: "Save Halifax Central Library" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(/couldn't save/i);
  });

  it("ignores a second click while a mutation is already in flight (no duplicate request)", async () => {
    mockAuthenticated();
    let resolveSave: () => void = () => {};
    mockSaveResource.mockImplementation(() => new Promise((resolve) => (resolveSave = () => resolve(undefined))));
    const user = userEvent.setup();
    renderWithQueryClient(<SaveResourceButton resourceId="r1" resourceName="Halifax Central Library" isSaved={false} />);

    const button = screen.getByRole("button", { name: "Save Halifax Central Library" });
    await user.click(button);
    // The button is disabled while pending, so userEvent won't dispatch a
    // second click at all — this is the mechanism that prevents a duplicate
    // request, verified directly.
    expect(button).toBeDisabled();
    await user.click(button);

    resolveSave();
    await waitFor(() => expect(mockSaveResource).toHaveBeenCalledTimes(1));
  });

  it("is keyboard operable", async () => {
    mockAuthenticated();
    mockSaveResource.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderWithQueryClient(<SaveResourceButton resourceId="r1" resourceName="Halifax Central Library" isSaved={false} />);

    await user.tab();
    expect(screen.getByRole("button", { name: "Save Halifax Central Library" })).toHaveFocus();
    await user.keyboard("{Enter}");

    await waitFor(() => expect(mockSaveResource).toHaveBeenCalled());
  });
});
