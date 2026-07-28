import { render, screen } from "@testing-library/react";
import { Pagination } from "./pagination";

describe("Pagination", () => {
  it("renders nothing when there is only one page", () => {
    const { container } = render(<Pagination page={0} totalPages={1} sort="name" />);
    expect(container).toBeEmptyDOMElement();
  });

  it("disables Previous on the first page and enables Next", () => {
    render(<Pagination page={0} totalPages={3} sort="name" />);

    expect(screen.getByText("Previous page")).not.toHaveAttribute("href");
    expect(screen.getByRole("link", { name: "Next page" })).toHaveAttribute("href", "/resources?page=1");
  });

  it("disables Next on the last page and enables Previous", () => {
    render(<Pagination page={2} totalPages={3} sort="name" />);

    expect(screen.getByText("Next page")).not.toHaveAttribute("href");
    expect(screen.getByRole("link", { name: "Previous page" })).toHaveAttribute("href", "/resources?page=1");
  });

  it("shows a human 1-based page count while the href stays 0-based", () => {
    render(<Pagination page={1} totalPages={5} sort="name" />);

    expect(screen.getByText("Page 2 of 5")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Next page" })).toHaveAttribute("href", "/resources?page=2");
  });

  it("preserves categoryId and sort in pagination links", () => {
    render(<Pagination page={0} totalPages={2} categoryId={4} sort="createdAt" />);

    expect(screen.getByRole("link", { name: "Next page" })).toHaveAttribute(
      "href",
      "/resources?categoryId=4&sort=createdAt&page=1",
    );
  });

  it("preserves the search query q in pagination links", () => {
    render(<Pagination page={0} totalPages={2} sort="name" q="library" />);

    expect(screen.getByRole("link", { name: "Next page" })).toHaveAttribute("href", "/resources?q=library&page=1");
  });
});
