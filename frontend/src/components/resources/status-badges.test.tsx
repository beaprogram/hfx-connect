import { render, screen } from "@testing-library/react";
import { CostBadge, HoursStatusBadge, VerificationBadge } from "./status-badges";

describe("CostBadge", () => {
  it("renders a readable label for each cost type", () => {
    render(<CostBadge costType="LOW_COST" />);
    expect(screen.getByText("Low cost")).toBeInTheDocument();
  });
});

describe("VerificationBadge", () => {
  it("marks a verified resource with a checkmark and the word Verified", () => {
    render(<VerificationBadge status="VERIFIED" />);
    expect(screen.getByText(/✓/)).toBeInTheDocument();
    expect(screen.getByText(/Verified/)).toBeInTheDocument();
  });

  it("does not show a checkmark for an unverified resource", () => {
    render(<VerificationBadge status="UNVERIFIED" />);
    expect(screen.queryByText(/✓/)).not.toBeInTheDocument();
    expect(screen.getByText(/Not yet verified/)).toBeInTheDocument();
  });
});

describe("HoursStatusBadge", () => {
  it("renders 'Open now' for OPEN", () => {
    render(<HoursStatusBadge status="OPEN" />);
    expect(screen.getByText("Open now")).toBeInTheDocument();
  });

  it("renders 'Closed' for CLOSED", () => {
    render(<HoursStatusBadge status="CLOSED" />);
    expect(screen.getByText("Closed")).toBeInTheDocument();
  });

  it("renders 'Hours unavailable' (never a false open/closed claim) for UNKNOWN", () => {
    render(<HoursStatusBadge status="UNKNOWN" />);
    expect(screen.getByText("Hours unavailable")).toBeInTheDocument();
    expect(screen.queryByText("Open now")).not.toBeInTheDocument();
    expect(screen.queryByText("Closed")).not.toBeInTheDocument();
  });
});
