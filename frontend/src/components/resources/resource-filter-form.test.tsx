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

  // ---- Cost/verification/openNow filter controls (Milestone 6B — see ADR-011) ----

  it("renders labelled cost, verification, and open-now controls", () => {
    render(<ResourceFilterForm categories={categories} sort="name" />);

    expect(screen.getByLabelText("Cost")).toBeInTheDocument();
    expect(screen.getByLabelText("Verification")).toBeInTheDocument();
    expect(screen.getByLabelText("Open now")).toBeInTheDocument();
  });

  it("reflects existing costType/verificationStatus/openNow URL values in the controls", () => {
    render(
      <ResourceFilterForm
        categories={categories}
        sort="name"
        costType="FREE"
        verificationStatus="VERIFIED"
        openNow={true}
      />,
    );

    expect(screen.getByLabelText("Cost")).toHaveValue("FREE");
    expect(screen.getByLabelText("Verification")).toHaveValue("VERIFIED");
    expect(screen.getByLabelText("Open now")).toBeChecked();
  });

  it("changing the cost filter navigates with costType set and resets to page 1", async () => {
    const user = userEvent.setup();
    render(<ResourceFilterForm categories={categories} sort="name" />);

    await user.selectOptions(screen.getByLabelText("Cost"), "FREE");

    expect(mockPush).toHaveBeenCalledWith("/resources?costType=FREE");
  });

  it("changing the verification filter navigates with verificationStatus set", async () => {
    const user = userEvent.setup();
    render(<ResourceFilterForm categories={categories} sort="name" />);

    await user.selectOptions(screen.getByLabelText("Verification"), "VERIFIED");

    expect(mockPush).toHaveBeenCalledWith("/resources?verificationStatus=VERIFIED");
  });

  it("checking the open-now checkbox navigates with openNow=true", async () => {
    const user = userEvent.setup();
    render(<ResourceFilterForm categories={categories} sort="name" />);

    await user.click(screen.getByLabelText("Open now"));

    expect(mockPush).toHaveBeenCalledWith("/resources?openNow=true");
  });

  it("combines q, categoryId, sort, costType, verificationStatus, and openNow together on submit", async () => {
    const user = userEvent.setup();
    render(
      <ResourceFilterForm
        categories={categories}
        categoryId={1}
        sort="createdAt"
        costType="FREE"
        verificationStatus="VERIFIED"
        openNow={true}
      />,
    );

    await user.type(screen.getByLabelText("Search"), "library");
    await user.click(screen.getByRole("button", { name: "Apply" }));

    expect(mockPush).toHaveBeenCalledWith(
      "/resources?categoryId=1&sort=createdAt&q=library&costType=FREE&verificationStatus=VERIFIED&openNow=true",
    );
  });

  it("shows a 'Reset all filters' link only when a filter is active, linking to a bare /resources", () => {
    const { rerender } = render(<ResourceFilterForm categories={categories} sort="name" />);
    expect(screen.queryByRole("link", { name: "Reset all filters" })).not.toBeInTheDocument();

    rerender(<ResourceFilterForm categories={categories} sort="name" openNow={true} />);
    expect(screen.getByRole("link", { name: "Reset all filters" })).toHaveAttribute("href", "/resources");
  });
});
