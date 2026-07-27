import { render, screen } from "@testing-library/react";
import { AuthProvider } from "@/lib/auth/auth-provider";
import { SiteHeader } from "./site-header";

jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: jest.fn(), replace: jest.fn() }),
}));

describe("SiteHeader", () => {
  it("renders the HFX Connect brand as a link to the homepage", () => {
    render(
      <AuthProvider>
        <SiteHeader />
      </AuthProvider>,
    );

    const brandLink = screen.getByRole("link", { name: "HFX Connect" });

    expect(brandLink).toHaveAttribute("href", "/");
  });

  it("links to the resource list", () => {
    render(
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
