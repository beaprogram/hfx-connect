import { render, screen } from "@testing-library/react";
import { ResourceDetail } from "./resource-detail";
import type { ResourceResponse } from "@/lib/validation/schemas";

const fullResource: ResourceResponse = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Halifax Central Library",
  slug: "halifax-central-library",
  description: "A full-service public library with free WiFi and study rooms.",
  addressLine1: "5381 Spring Garden Rd",
  addressLine2: null,
  city: "Halifax",
  province: "NS",
  postalCode: "B3J 2K9",
  phone: "9025550100",
  email: "info@example.org",
  websiteUrl: "https://example.org",
  costType: "FREE",
  costDetails: "Free with a library card.",
  eligibility: "Open to the public.",
  verificationStatus: "VERIFIED",
  active: true,
  category: { id: 1, name: "Study Spaces", slug: "study-spaces" },
  createdAt: "2026-07-15T00:00:00Z",
  updatedAt: "2026-07-15T00:00:00Z",
};

describe("ResourceDetail", () => {
  it("renders the resource name as the only h1", () => {
    render(<ResourceDetail resource={fullResource} />);

    const headings = screen.getAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveTextContent("Halifax Central Library");
  });

  it("links the category to the filtered resource list", () => {
    render(<ResourceDetail resource={fullResource} />);

    expect(screen.getByRole("link", { name: "Study Spaces" })).toHaveAttribute("href", "/resources?categoryId=1");
  });

  it("uses tel:, mailto:, and a safe external link for contact fields", () => {
    render(<ResourceDetail resource={fullResource} />);

    expect(screen.getByRole("link", { name: "9025550100" })).toHaveAttribute("href", "tel:9025550100");
    expect(screen.getByRole("link", { name: "info@example.org" })).toHaveAttribute("href", "mailto:info@example.org");

    const websiteLink = screen.getByRole("link", { name: /^https:\/\/example\.org/ });
    expect(websiteLink).toHaveAttribute("href", "https://example.org");
    expect(websiteLink).toHaveAttribute("target", "_blank");
    expect(websiteLink).toHaveAttribute("rel", expect.stringContaining("noopener"));
  });

  it("renders the address on separate lines", () => {
    render(<ResourceDetail resource={fullResource} />);

    expect(screen.getByText("5381 Spring Garden Rd")).toBeInTheDocument();
    expect(screen.getByText("Halifax, NS B3J 2K9")).toBeInTheDocument();
  });

  it("shows verified status and eligibility when present", () => {
    render(<ResourceDetail resource={fullResource} />);

    expect(screen.getByText(/Verified/)).toBeInTheDocument();
    expect(screen.getByText("Open to the public.")).toBeInTheDocument();
  });

  it("omits Contact, Eligibility, and Location sections entirely when the backend returns no data for them", () => {
    const minimalResource: ResourceResponse = {
      ...fullResource,
      addressLine1: null,
      addressLine2: null,
      city: null,
      province: null,
      postalCode: null,
      phone: null,
      email: null,
      websiteUrl: null,
      eligibility: null,
    };

    render(<ResourceDetail resource={minimalResource} />);

    expect(screen.queryByRole("heading", { name: "Contact" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Eligibility" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Location" })).not.toBeInTheDocument();
  });

  it("never renders an 'Accessibility' section, since the backend has no such field", () => {
    render(<ResourceDetail resource={fullResource} />);

    expect(screen.queryByText(/Accessibility/i)).not.toBeInTheDocument();
  });
});
