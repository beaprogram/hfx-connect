import { Badge } from "@/components/feedback/badge";
import { organizationVerificationStatusLabel, ownershipClaimStatusLabel } from "@/lib/formatting/labels";
import type { OrganizationVerificationStatus, OwnershipClaimStatus } from "@/lib/validation/schemas";

/** An organization's verification status (Milestone 10A) — text label always paired with colour, never colour alone. */
export function OrganizationVerificationBadge({ status }: { status: OrganizationVerificationStatus }) {
  const tone = status === "VERIFIED" ? "positive" : status === "PENDING_VERIFICATION" ? "info" : "neutral";
  return <Badge tone={tone}>{organizationVerificationStatusLabel(status)}</Badge>;
}

/** A resource-ownership claim's status (Milestone 10A) — text label always paired with colour, never colour alone. */
export function OwnershipClaimStatusBadge({ status }: { status: OwnershipClaimStatus }) {
  const tone = status === "APPROVED" ? "positive" : status === "PENDING_REVIEW" ? "info" : "neutral";
  return <Badge tone={tone}>{ownershipClaimStatusLabel(status)}</Badge>;
}
