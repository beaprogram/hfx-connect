import { render, screen } from "@testing-library/react";
import { SiteFooter } from "./site-footer";

describe("SiteFooter", () => {
  it("links to the public GitHub repository", () => {
    render(<SiteFooter />);

    const repoLink = screen.getByRole("link", {
      name: /view source on github/i,
    });

    expect(repoLink).toHaveAttribute(
      "href",
      "https://github.com/beaprogram/hfx-connect",
    );
  });
});
