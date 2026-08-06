import type { Metadata } from "next";
import { ModerationRoute } from "@/components/moderation/moderation-route";
import { CorrectionReportReviewDetail } from "@/components/moderation/correction-report-review-detail";

export const metadata: Metadata = {
  title: "Review Correction Report — HFX Connect",
};

interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function ModerationCorrectionReportDetailPage({ params }: PageProps) {
  const { id } = await params;
  return (
    <ModerationRoute>
      <CorrectionReportReviewDetail reportId={id} />
    </ModerationRoute>
  );
}
