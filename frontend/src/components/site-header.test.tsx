import { render, screen } from "@testing-library/react";
import { SiteHeader } from "./site-header";

describe("SiteHeader", () => {
  it("renders the HFX Connect brand as a link to the homepage", () => {
    render(<SiteHeader />);

    const brandLink = screen.getByRole("link", { name: "HFX Connect" });

    expect(brandLink).toHaveAttribute("href", "/");
  });
});
