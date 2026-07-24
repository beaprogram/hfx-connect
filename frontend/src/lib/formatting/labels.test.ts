import { costTypeLabel, verificationStatusLabel } from "./labels";

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
