import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { ResourceCard } from "./resource-card";
import { useAuth } from "@/lib/auth/auth-provider";
import type { ResourceSummaryResponse } from "@/lib/validation/schemas";

jest.mock("@/lib/auth/auth-provider");
const mockUseAuth = useAuth as jest.MockedFunction<typeof useAuth>;

// The authenticated save/remove control uses TanStack Query mutations
// (Milestone 8A), so every render needs a real QueryClientProvider ancestor.
function renderWithQueryClient(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

const baseResource: ResourceSummaryResponse = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Halifax Central Library",
  slug: "halifax-central-library",
  city: "Halifax",
  province: "NS",
  costType: "FREE",
  verificationStatus: "UNVERIFIED",
  active: true,
  category: { id: 1, name: "Study Spaces", slug: "study-spaces" },
  createdAt: "2026-07-15T00:00:00Z",
  hoursStatus: "UNKNOWN",
  openNow: null,
};

describe("ResourceCard", () => {
  beforeEach(() => {
    // Unauthenticated by default — the save control renders as a "Sign in
    // to save" link in this state (Milestone 8A). Tests that need the
    // authenticated Save/Remove button override this per-test.
    mockUseAuth.mockReturnValue({
      state: { status: "unauthenticated" },
      login: jest.fn(),
      logout: jest.fn(),
      getValidAccessToken: jest.fn(),
    });
  });

  it("links its heading to the resource detail page", () => {
    renderWithQueryClient(<ResourceCard resource={baseResource} />);

    const link = screen.getByRole("link", { name: "Halifax Central Library" });
    expect(link).toHaveAttribute("href", "/resources/halifax-central-library");
  });

  it("shows the category and city/province summary", () => {
    renderWithQueryClient(<ResourceCard resource={baseResource} />);

    expect(screen.getByText(/Study Spaces/)).toBeInTheDocument();
    expect(screen.getByText(/Halifax, NS/)).toBeInTheDocument();
  });

  it("shows readable cost and verification badges, not raw enum values", () => {
    renderWithQueryClient(<ResourceCard resource={baseResource} />);

    expect(screen.getByText("Free")).toBeInTheDocument();
    expect(screen.getByText(/Not yet verified/)).toBeInTheDocument();
    expect(screen.queryByText("FREE")).not.toBeInTheDocument();
    expect(screen.queryByText("UNVERIFIED")).not.toBeInTheDocument();
  });

  it("omits the location summary entirely when city/province are absent", () => {
    renderWithQueryClient(<ResourceCard resource={{ ...baseResource, city: null, province: null }} />);

    expect(screen.getByText("Study Spaces")).toBeInTheDocument();
  });

  it("has no nested interactive controls — the title link and the save control are siblings, not nested", () => {
    renderWithQueryClient(<ResourceCard resource={baseResource} />);

    // Unauthenticated: the title link plus a "Sign in to save" link — two
    // siblings, neither nested inside the other.
    const links = screen.getAllByRole("link");
    expect(links).toHaveLength(2);
    expect(links[0]).toHaveAccessibleName("Halifax Central Library");
    expect(links[1]).toHaveAccessibleName(/sign in to save/i);
  });

  it("shows a Save button (not a second link) once authenticated", () => {
    mockUseAuth.mockReturnValue({
      state: {
        status: "authenticated",
        user: {
          id: "22222222-2222-2222-2222-222222222222",
          email: "student@example.org",
          role: "USER",
          status: "ACTIVE",
          emailVerified: false,
          createdAt: "2026-07-15T00:00:00Z",
        },
        accessToken: "token-1",
        expiresAt: Date.now() + 900_000,
      },
      login: jest.fn(),
      logout: jest.fn(),
      getValidAccessToken: jest.fn(),
    });

    renderWithQueryClient(<ResourceCard resource={baseResource} isSaved={false} />);

    expect(screen.getAllByRole("link")).toHaveLength(1);
    expect(screen.getByRole("button", { name: "Save Halifax Central Library" })).toBeInTheDocument();
  });

  it("shows 'Hours unavailable' rather than an open/closed claim when hoursStatus is UNKNOWN", () => {
    renderWithQueryClient(<ResourceCard resource={baseResource} />);

    expect(screen.getByText("Hours unavailable")).toBeInTheDocument();
    expect(screen.queryByText("Open now")).not.toBeInTheDocument();
    expect(screen.queryByText("Closed")).not.toBeInTheDocument();
  });

  it("shows 'Open now' when the resource is currently open", () => {
    renderWithQueryClient(<ResourceCard resource={{ ...baseResource, hoursStatus: "OPEN", openNow: true }} />);

    expect(screen.getByText("Open now")).toBeInTheDocument();
  });

  it("shows 'Closed' when the resource is currently closed", () => {
    renderWithQueryClient(<ResourceCard resource={{ ...baseResource, hoursStatus: "CLOSED", openNow: false }} />);

    expect(screen.getByText("Closed")).toBeInTheDocument();
  });
});
