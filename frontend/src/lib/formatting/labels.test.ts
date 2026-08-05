import { costTypeLabel, verificationStatusLabel, contributionStatusLabel, issueTypeLabel } from "./labels";

describe("costTypeLabel", () => {
  it.each([
    ["FREE", "Free"],
    ["LOW_COST", "Low cost"],
    ["PAID", "Paid"],
    ["UNKNOWN", "Cost not listed"],
  ] as const)("formats %s as %s", (costType, expected) => {
    expect(costTypeLabel(costType)).toBe(expected);
  });
});

describe("verificationStatusLabel", () => {
  it.each([
    ["VERIFIED", "Verified"],
    ["UNVERIFIED", "Not yet verified"],
  ] as const)("formats %s as %s", (status, expected) => {
    expect(verificationStatusLabel(status)).toBe(expected);
  });
});

describe("contributionStatusLabel", () => {
  it.each([
    ["PENDING_REVIEW", "Pending review"],
    ["APPROVED", "Approved"],
    ["REJECTED", "Rejected"],
    ["WITHDRAWN", "Withdrawn"],
  ] as const)("formats %s as %s", (status, expected) => {
    expect(contributionStatusLabel(status)).toBe(expected);
  });
});

describe("issueTypeLabel", () => {
  it("formats every issue type to a non-empty, distinct label", () => {
    const issueTypes = [
      "GENERAL_INFORMATION",
      "ADDRESS",
      "CONTACT_INFORMATION",
      "OPERATING_HOURS",
      "ELIGIBILITY",
      "ACCESSIBILITY",
      "COST",
      "RESOURCE_CLOSED",
      "DUPLICATE_RESOURCE",
      "OTHER",
    ] as const;
    const labels = issueTypes.map(issueTypeLabel);
    expect(labels.every((label) => label.length > 0)).toBe(true);
    expect(new Set(labels).size).toBe(issueTypes.length);
  });
});
