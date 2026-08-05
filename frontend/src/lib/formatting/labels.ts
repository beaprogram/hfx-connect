import type { ContributionStatus, CostType, DayOfWeek, HoursStatus, IssueType, VerificationStatus } from "@/lib/validation/schemas";

const COST_TYPE_LABELS: Record<CostType, string> = {
  FREE: "Free",
  LOW_COST: "Low cost",
  PAID: "Paid",
  UNKNOWN: "Cost not listed",
};

export function costTypeLabel(costType: CostType): string {
  return COST_TYPE_LABELS[costType];
}

const VERIFICATION_STATUS_LABELS: Record<VerificationStatus, string> = {
  VERIFIED: "Verified",
  UNVERIFIED: "Not yet verified",
};

export function verificationStatusLabel(status: VerificationStatus): string {
  return VERIFICATION_STATUS_LABELS[status];
}

/**
 * A resource card/detail's readable open-status label — never colour alone
 * (see Badge's own docstring), and never claims "Open now" when the status
 * is UNKNOWN (Milestone 6B — see ADR-011).
 */
export function hoursStatusLabel(status: HoursStatus): string {
  switch (status) {
    case "OPEN":
      return "Open now";
    case "CLOSED":
      return "Closed";
    case "UNKNOWN":
      return "Hours unavailable";
  }
}

const DAY_OF_WEEK_LABELS: Record<DayOfWeek, string> = {
  MONDAY: "Monday",
  TUESDAY: "Tuesday",
  WEDNESDAY: "Wednesday",
  THURSDAY: "Thursday",
  FRIDAY: "Friday",
  SATURDAY: "Saturday",
  SUNDAY: "Sunday",
};

export function dayOfWeekLabel(day: DayOfWeek): string {
  return DAY_OF_WEEK_LABELS[day];
}

/**
 * Formats a backend ISO local-time string ("09:00:00") into a 12-hour
 * readable label ("9:00 AM") without touching timezone meaning at all — the
 * backend has already resolved the value to America/Halifax local time (see
 * ADR-011); this is pure display formatting of the same wall-clock value.
 * Returns the raw input unchanged if it doesn't match the expected shape,
 * rather than throwing, since this only ever renders already-validated API data.
 */
const CONTRIBUTION_STATUS_LABELS: Record<ContributionStatus, string> = {
  PENDING_REVIEW: "Pending review",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  WITHDRAWN: "Withdrawn",
};

/** A resource submission's or correction report's readable review-status label (Milestone 8B) — never colour alone. */
export function contributionStatusLabel(status: ContributionStatus): string {
  return CONTRIBUTION_STATUS_LABELS[status];
}

const ISSUE_TYPE_LABELS: Record<IssueType, string> = {
  GENERAL_INFORMATION: "General information",
  ADDRESS: "Address",
  CONTACT_INFORMATION: "Contact information",
  OPERATING_HOURS: "Operating hours",
  ELIGIBILITY: "Eligibility",
  ACCESSIBILITY: "Accessibility",
  COST: "Cost",
  RESOURCE_CLOSED: "Resource closed",
  DUPLICATE_RESOURCE: "Duplicate listing",
  OTHER: "Other",
};

export function issueTypeLabel(issueType: IssueType): string {
  return ISSUE_TYPE_LABELS[issueType];
}

export function formatLocalTime(time: string): string {
  const match = /^(\d{1,2}):(\d{2})/.exec(time);
  if (!match) return time;
  const hour24 = Number(match[1]);
  const minute = match[2];
  const period = hour24 >= 12 ? "PM" : "AM";
  const hour12 = hour24 % 12 === 0 ? 12 : hour24 % 12;
  return `${hour12}:${minute} ${period}`;
}
