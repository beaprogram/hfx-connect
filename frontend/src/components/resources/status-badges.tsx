import { Badge } from "@/components/feedback/badge";
import { costTypeLabel, hoursStatusLabel, verificationStatusLabel } from "@/lib/formatting/labels";
import type { CostType, HoursStatus, VerificationStatus } from "@/lib/validation/schemas";

export function CostBadge({ costType }: { costType: CostType }) {
  return <Badge tone={costType === "FREE" ? "positive" : "neutral"}>{costTypeLabel(costType)}</Badge>;
}

export function VerificationBadge({ status }: { status: VerificationStatus }) {
  return (
    <Badge tone={status === "VERIFIED" ? "positive" : "neutral"}>
      {status === "VERIFIED" ? "✓ " : ""}
      {verificationStatusLabel(status)}
    </Badge>
  );
}

/**
 * Never shown as "Open now" when the status is UNKNOWN (no schedule at
 * all) — see ADR-011's "Status Model". Colour is always paired with the
 * text label itself, never colour alone.
 */
export function HoursStatusBadge({ status }: { status: HoursStatus }) {
  return <Badge tone={status === "OPEN" ? "positive" : "neutral"}>{hoursStatusLabel(status)}</Badge>;
}
