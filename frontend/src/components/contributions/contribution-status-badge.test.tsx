import { render, screen } from "@testing-library/react";
import { ContributionStatusBadge } from "./contribution-status-badge";

describe("ContributionStatusBadge", () => {
  it.each([
    ["PENDING_REVIEW", "Pending review"],
    ["APPROVED", "Approved"],
    ["REJECTED", "Rejected"],
    ["WITHDRAWN", "Withdrawn"],
  ] as const)("renders a visible text label for %s", (status, label) => {
    render(<ContributionStatusBadge status={status} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });
});
