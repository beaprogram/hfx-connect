import { render, screen } from "@testing-library/react";
import { CategoryCard } from "./category-card";
import type { CategoryResponse } from "@/lib/validation/schemas";

const baseCategory: CategoryResponse = {
  id: 1,
  name: "Food Assistance",
  slug: "food-assistance",
  description: "Food banks and community meals.",
  active: true,
  createdAt: "2026-07-15T00:00:00Z",
  updatedAt: "2026-07-15T00:00:00Z",
};

describe("CategoryCard", () => {
  it("links to the resource list filtered by this category's id", () => {
    render(<CategoryCard category={baseCategory} />);

    const link = screen.getByRole("link", { name: "Food Assistance" });
    expect(link).toHaveAttribute("href", "/resources?categoryId=1");
  });

  it("shows the description when present", () => {
    render(<CategoryCard category={baseCategory} />);

    expect(screen.getByText("Food banks and community meals.")).toBeInTheDocument();
  });

  it("renders no description paragraph when the category has none", () => {
    render(<CategoryCard category={{ ...baseCategory, description: null }} />);

    expect(screen.queryByText("Food banks and community meals.")).not.toBeInTheDocument();
  });
});
