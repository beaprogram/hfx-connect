import { renderHook, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import {
  useSavedResourceStatusMap,
  useSaveResourceMutation,
  useRemoveSavedResourceMutation,
} from "./use-saved-resources";
import { useAuth } from "@/lib/auth/auth-provider";
import {
  getSavedResourceStatus,
  saveResource as apiSaveResource,
  removeSavedResource as apiRemoveSavedResource,
} from "@/lib/api/saved-resources";

jest.mock("@/lib/auth/auth-provider");
jest.mock("@/lib/api/saved-resources");

const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;
const mockGetSavedResourceStatus = getSavedResourceStatus as jest.MockedFunction<typeof getSavedResourceStatus>;
const mockApiSaveResource = apiSaveResource as jest.MockedFunction<typeof apiSaveResource>;
const mockApiRemoveSavedResource = apiRemoveSavedResource as jest.MockedFunction<typeof apiRemoveSavedResource>;

function wrapper({ children }: { children: ReactNode }) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

function mockAuthenticated(userId = "user-1") {
  mockUseAuth.mockReturnValue({
    state: {
      status: "authenticated",
      user: { id: userId, email: "a@example.org", role: "USER", status: "ACTIVE", emailVerified: false, createdAt: "2026-07-15T00:00:00Z" },
      accessToken: "token-1",
      expiresAt: Date.now() + 900_000,
    },
    login: jest.fn(),
    logout: jest.fn(),
    getValidAccessToken: jest.fn().mockResolvedValue("token-1"),
  });
}

function mockUnauthenticated() {
  mockUseAuth.mockReturnValue({
    state: { status: "unauthenticated" },
    login: jest.fn(),
    logout: jest.fn(),
    getValidAccessToken: jest.fn().mockResolvedValue(null),
  });
}

describe("useSavedResourceStatusMap", () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it("does not request status while signed out", () => {
    mockUnauthenticated();

    renderHook(() => useSavedResourceStatusMap(["a", "b"]), { wrapper });

    expect(mockGetSavedResourceStatus).not.toHaveBeenCalled();
  });

  it("does not request status for an empty id list", () => {
    mockAuthenticated();

    renderHook(() => useSavedResourceStatusMap([]), { wrapper });

    expect(mockGetSavedResourceStatus).not.toHaveBeenCalled();
  });

  it("returns the saved subset once authenticated and ids are supplied", async () => {
    mockAuthenticated();
    mockGetSavedResourceStatus.mockResolvedValue({ savedResourceIds: ["a"] });

    const { result } = renderHook(() => useSavedResourceStatusMap(["a", "b"]), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));
    expect(result.current.savedIds.has("a")).toBe(true);
    expect(result.current.savedIds.has("b")).toBe(false);
  });

  it("requests status only once for the same set of ids regardless of array identity", async () => {
    mockAuthenticated();
    mockGetSavedResourceStatus.mockResolvedValue({ savedResourceIds: [] });

    const { rerender } = renderHook(({ ids }) => useSavedResourceStatusMap(ids), {
      wrapper,
      initialProps: { ids: ["a", "b"] },
    });
    await waitFor(() => expect(mockGetSavedResourceStatus).toHaveBeenCalledTimes(1));

    // A brand-new array instance with the same ids should not refetch.
    rerender({ ids: ["a", "b"] });

    await new Promise((resolve) => setTimeout(resolve, 10));
    expect(mockGetSavedResourceStatus).toHaveBeenCalledTimes(1);
  });
});

describe("useSaveResourceMutation", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAuthenticated();
  });

  it("calls the save API with the resolved access token", async () => {
    mockApiSaveResource.mockResolvedValue(undefined);
    const { result } = renderHook(() => useSaveResourceMutation(), { wrapper });

    result.current.mutate("resource-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApiSaveResource).toHaveBeenCalledWith("resource-1", "token-1");
  });

  it("surfaces a failure via isError rather than throwing uncaught", async () => {
    mockApiSaveResource.mockRejectedValue(new Error("network down"));
    const { result } = renderHook(() => useSaveResourceMutation(), { wrapper });

    result.current.mutate("resource-1");

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});

describe("useRemoveSavedResourceMutation", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAuthenticated();
  });

  it("calls the remove API with the resolved access token", async () => {
    mockApiRemoveSavedResource.mockResolvedValue(undefined);
    const { result } = renderHook(() => useRemoveSavedResourceMutation(), { wrapper });

    result.current.mutate("resource-1");

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(mockApiRemoveSavedResource).toHaveBeenCalledWith("resource-1", "token-1");
  });
});
