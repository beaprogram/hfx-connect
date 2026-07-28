import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ResourceFilterForm } from "./resource-filter-form";
import type { CategoryResponse } from "@/lib/validation/schemas";

const mockPush = jest.fn();
jest.mock("next/navigation", () => ({
  useRouter: () => ({ push: mockPush }),
}));

const categories: CategoryResponse[] = [
  { id: 1, name: "Study Spaces", slug: "study-spaces", description: null, active: true, createdAt: "2026-07-15T00:00:00Z", updatedAt: "2026-07-15T00:00:00Z" },
];

describe("ResourceFilterForm", () => {
  beforeEach(() => {
    mockPush.mockClear();
  });

  it("renders a labelled search field", () => {
    render(<ResourceFilterForm categories={categories} sort="name" />);

    expect(screen.getByLabelText("Search")).toBeInTheDocument();
  });

  it("shows the current q value in the search field", () => {
    render(<ResourceFilterForm categories={categories} sort="name" q="food bank" />);

    expect(screen.getByLabelText("Search")).toHaveValue("food bank");
  });

  it("submitting the form navigates with q set and no page parameter (resets to page 1)", async () => {
    const user = userEvent.setup();
    render(<ResourceFilterForm categories={categories} sort="name" />);

    await user.type(screen.getByLabelText("Search"), "library");
    await user.click(screen.getByRole("button", { name: "Apply" }));

    expect(mockPush).toHaveBeenCalledWith("/resources?q=library");
  });

  it("preserves category and sort selections alongside the search query on submit", async () => {
    const user = userEvent.setup();
    render(<ResourceFilterForm categories={categories} categoryId={1} sort="createdAt" q="old query" />);

    const searchInput = screen.getByLabelText("Search");
    await user.clear(searchInput);
    await user.type(searchInput, "new query");
    await user.click(screen.getByRole("button", { name: "Apply" }));

    expect(mockPush).toHaveBeenCalledWith("/resources?categoryId=1&sort=createdAt&q=new+query");
  });

  it("does not submit on every keystroke", async () => {
    const user = userEvent.setup();
    render(<ResourceFilterForm categories={categories} sort="name" />);

    await user.type(screen.getByLabelText("Search"), "library");

    expect(mockPush).not.toHaveBeenCalled();
  });

  it("shows a Clear search link only when a query is active, which removes q", () => {
    const { rerender } = render(<ResourceFilterForm categories={categories} sort="name" />);
    expect(screen.queryByRole("link", { name: "Clear search" })).not.toBeInTheDocument();

    rerender(<ResourceFilterForm categories={categories} categoryId={1} sort="name" q="library" />);
    expect(screen.getByRole("link", { name: "Clear search" })).toHaveAttribute("href", "/resources?categoryId=1");
  });

  it("submits via Enter key in the search field", async () => {
    const user = userEvent.setup();
    render(<ResourceFilterForm categories={categories} sort="name" />);

    await user.type(screen.getByLabelText("Search"), "library{Enter}");

    expect(mockPush).toHaveBeenCalledWith("/resources?q=library");
  });
});
