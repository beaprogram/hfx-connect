import { render, screen } from "@testing-library/react";
import { ResourceCard } from "./resource-card";
import type { ResourceSummaryResponse } from "@/lib/validation/schemas";

const baseResource: ResourceSummaryResponse = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Halifax Central Library",
  slug: "halifax-central-library",
  city: "Halifax",
  province: "NS",
  costType: "FREE",
  verificationStatus: "UNVERIFIED",
  active: true,
  category: { id: 1, name: "Study Spaces", slug: "study-spaces" },
  createdAt: "2026-07-15T00:00:00Z",
  hoursStatus: "UNKNOWN",
  openNow: null,
};

describe("ResourceCard", () => {
  it("links its heading to the resource detail page", () => {
    render(<ResourceCard resource={baseResource} />);

    const link = screen.getByRole("link", { name: "Halifax Central Library" });
    expect(link).toHaveAttribute("href", "/resources/halifax-central-library");
  });

  it("shows the category and city/province summary", () => {
    render(<ResourceCard resource={baseResource} />);

    expect(screen.getByText(/Study Spaces/)).toBeInTheDocument();
    expect(screen.getByText(/Halifax, NS/)).toBeInTheDocument();
  });

  it("shows readable cost and verification badges, not raw enum values", () => {
    render(<ResourceCard resource={baseResource} />);

    expect(screen.getByText("Free")).toBeInTheDocument();
    expect(screen.getByText(/Not yet verified/)).toBeInTheDocument();
    expect(screen.queryByText("FREE")).not.toBeInTheDocument();
    expect(screen.queryByText("UNVERIFIED")).not.toBeInTheDocument();
  });

  it("omits the location summary entirely when city/province are absent", () => {
    render(<ResourceCard resource={{ ...baseResource, city: null, province: null }} />);

    expect(screen.getByText("Study Spaces")).toBeInTheDocument();
  });

  it("has only one interactive element per card (no nested interactive controls)", () => {
    render(<ResourceCard resource={baseResource} />);

    expect(screen.getAllByRole("link")).toHaveLength(1);
  });

  it("shows 'Hours unavailable' rather than an open/closed claim when hoursStatus is UNKNOWN", () => {
    render(<ResourceCard resource={baseResource} />);

    expect(screen.getByText("Hours unavailable")).toBeInTheDocument();
    expect(screen.queryByText("Open now")).not.toBeInTheDocument();
    expect(screen.queryByText("Closed")).not.toBeInTheDocument();
  });

  it("shows 'Open now' when the resource is currently open", () => {
    render(<ResourceCard resource={{ ...baseResource, hoursStatus: "OPEN", openNow: true }} />);

    expect(screen.getByText("Open now")).toBeInTheDocument();
  });

  it("shows 'Closed' when the resource is currently closed", () => {
    render(<ResourceCard resource={{ ...baseResource, hoursStatus: "CLOSED", openNow: false }} />);

    expect(screen.getByText("Closed")).toBeInTheDocument();
  });
});
