import { Badge } from "@/components/feedback/badge";
import { costTypeLabel, verificationStatusLabel } from "@/lib/formatting/labels";
import type { CostType, VerificationStatus } from "@/lib/validation/schemas";

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
