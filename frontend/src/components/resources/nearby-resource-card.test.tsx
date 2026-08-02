import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NearbyResourceCard } from "./nearby-resource-card";
import type { NearbyResourceSummaryResponse } from "@/lib/validation/schemas";

const resource: NearbyResourceSummaryResponse = {
  id: "11111111-1111-1111-1111-111111111111",
  name: "Halifax Central Library",
  slug: "halifax-central-library",
  city: "Halifax",
  province: "NS",
  costType: "FREE",
  verificationStatus: "VERIFIED",
  active: true,
  category: { id: 1, name: "Study Spaces", slug: "study-spaces" },
  createdAt: "2026-07-15T00:00:00Z",
  hoursStatus: "OPEN",
  openNow: true,
  latitude: 44.6488,
  longitude: -63.5752,
  distanceMeters: 850,
};

describe("NearbyResourceCard", () => {
  it("shows the resource name, category, distance, and a details link", () => {
    render(<NearbyResourceCard resource={resource} selected={false} onSelect={jest.fn()} />);

    expect(screen.getByRole("button", { name: "Halifax Central Library" })).toBeInTheDocument();
    expect(screen.getByText(/Study Spaces/)).toBeInTheDocument();
    expect(screen.getByText(/850 m away/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View details" })).toHaveAttribute(
      "href",
      "/resources/halifax-central-library",
    );
  });

  it("formats distances at or above 1 km in kilometres", () => {
    render(<NearbyResourceCard resource={{ ...resource, distanceMeters: 1850 }} selected={false} onSelect={jest.fn()} />);
    expect(screen.getByText(/1\.9 km away/)).toBeInTheDocument();
  });

  it("calls onSelect with the resource id when activated", async () => {
    const user = userEvent.setup();
    const onSelect = jest.fn();
    render(<NearbyResourceCard resource={resource} selected={false} onSelect={onSelect} />);

    await user.click(screen.getByRole("button", { name: "Halifax Central Library" }));

    expect(onSelect).toHaveBeenCalledWith(resource.id);
  });

  it("communicates selection through a visible text label, not colour alone", () => {
    render(<NearbyResourceCard resource={resource} selected onSelect={jest.fn()} />);

    expect(screen.getByText("Selected")).toBeInTheDocument();
    expect(screen.getByRole("article")).toHaveAttribute("aria-current", "true");
  });

  it("does not show a 'Selected' label when not selected", () => {
    render(<NearbyResourceCard resource={resource} selected={false} onSelect={jest.fn()} />);
    expect(screen.queryByText("Selected")).not.toBeInTheDocument();
  });
});
