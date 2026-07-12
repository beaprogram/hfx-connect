import { render, screen } from "@testing-library/react";
import Home from "./page";

describe("Home", () => {
  it("renders the primary heading describing the product", () => {
    render(<Home />);

    const heading = screen.getByRole("heading", { level: 1 });

    expect(heading).toBeInTheDocument();
    expect(heading).toHaveTextContent(/Halifax/i);
  });

  it("links to the public GitHub repository for project progress", () => {
    render(<Home />);

    const repoLink = screen.getByRole("link", {
      name: /public GitHub repository/i,
    });

    expect(repoLink).toHaveAttribute(
      "href",
      "https://github.com/beaprogram/hfx-connect",
    );
  });
});
