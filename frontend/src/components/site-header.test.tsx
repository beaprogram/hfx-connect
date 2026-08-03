import { render, screen } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { AuthProvider } from "@/lib/auth/auth-provider";
import { SiteHeader } from "./site-header";

jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: jest.fn(), replace: jest.fn() }),
}));

// AuthProvider reads useQueryClient() (Milestone 8A), so it needs a real
// QueryClientProvider ancestor, same as any other TanStack-Query consumer.
function renderWithQueryClient(ui: ReactElement) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{ui}</QueryClientProvider>);
}

describe("SiteHeader", () => {
  it("renders the HFX Connect brand as a link to the homepage", () => {
    renderWithQueryClient(
      <AuthProvider>
        <SiteHeader />
      </AuthProvider>,
    );

    const brandLink = screen.getByRole("link", { name: "HFX Connect" });

    expect(brandLink).toHaveAttribute("href", "/");
  });

  it("links to the resource list", () => {
    renderWithQueryClient(
      <AuthProvider>
        <SiteHeader />
      </AuthProvider>,
    );

    const browseLinks = screen.getAllByRole("link", { name: "Browse resources" });
    expect(browseLinks.length).toBeGreaterThan(0);
    for (const link of browseLinks) {
      expect(link).toHaveAttribute("href", "/resources");
    }
  });
});
