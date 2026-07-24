import { render, screen } from "@testing-library/react";
import { SiteHeader } from "./site-header";

describe("SiteHeader", () => {
  it("renders the HFX Connect brand as a link to the homepage", () => {
    render(<SiteHeader />);

    const brandLink = screen.getByRole("link", { name: "HFX Connect" });

    expect(brandLink).toHaveAttribute("href", "/");
  });

  it("links to the resource list", () => {
    render(<SiteHeader />);

    const browseLinks = screen.getAllByRole("link", { name: "Browse resources" });
    expect(browseLinks.length).toBeGreaterThan(0);
    for (const link of browseLinks) {
      expect(link).toHaveAttribute("href", "/resources");
    }
  });
});
