import { Badge } from "@/components/feedback/badge";
import { contributionStatusLabel } from "@/lib/formatting/labels";
import type { ContributionStatus } from "@/lib/validation/schemas";

/** A resource submission's or correction report's status (Milestone 8B) — text label always paired with colour, never colour alone. */
export function ContributionStatusBadge({ status }: { status: ContributionStatus }) {
  const tone = status === "APPROVED" ? "positive" : status === "REJECTED" ? "neutral" : "info";
  return <Badge tone={tone}>{contributionStatusLabel(status)}</Badge>;
}
