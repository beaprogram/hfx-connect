import { render, screen } from "@testing-library/react";
import Home from "./page";
import { getCategories } from "@/lib/api/categories";
import { getResources } from "@/lib/api/resources";
import type { CategoryPageResponse, ResourcePageResponse } from "@/lib/validation/schemas";

jest.mock("@/lib/api/categories");
jest.mock("@/lib/api/resources");

const mockGetCategories = getCategories as jest.MockedFunction<typeof getCategories>;
const mockGetResources = getResources as jest.MockedFunction<typeof getResources>;

const emptyCategoryPage: CategoryPageResponse = { content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 };
const emptyResourcePage: ResourcePageResponse = { content: [], page: 0, size: 3, totalElements: 0, totalPages: 0 };

describe("Home", () => {
  beforeEach(() => {
    mockGetCategories.mockResolvedValue(emptyCategoryPage);
    mockGetResources.mockResolvedValue(emptyResourcePage);
  });

  it("renders the primary heading describing the product", async () => {
    render(await Home());

    const heading = screen.getByRole("heading", { level: 1 });
    expect(heading).toBeInTheDocument();
    expect(heading).toHaveTextContent(/Halifax/i);
  });

  it("links the primary action to the resources list", async () => {
    render(await Home());

    const browseLink = screen.getByRole("link", { name: "Browse all resources" });
    expect(browseLink).toHaveAttribute("href", "/resources");
  });

  it("shows an empty-state message when no categories exist yet", async () => {
    render(await Home());

    expect(screen.getByText(/Categories aren't available yet/i)).toBeInTheDocument();
  });

  it("shows an inline error when categories fail to load", async () => {
    mockGetCategories.mockRejectedValue(new Error("network down"));

    render(await Home());

    expect(screen.getByText(/Categories couldn't be loaded right now/i)).toBeInTheDocument();
  });

  it("shows an inline error when the resource preview fails independently of categories", async () => {
    mockGetResources.mockRejectedValue(new Error("network down"));

    render(await Home());

    expect(screen.getByText(/Recently added resources couldn't be loaded right now/i)).toBeInTheDocument();
    // Categories still succeeded and should not show the error state.
    expect(screen.queryByText(/Categories couldn't be loaded right now/i)).not.toBeInTheDocument();
  });
});
