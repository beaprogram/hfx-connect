import type { CostType, VerificationStatus } from "@/lib/validation/schemas";

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
