import type { Metadata } from "next";
import { ModerationRoute } from "@/components/moderation/moderation-route";
import { ResourceSubmissionReviewDetail } from "@/components/moderation/resource-submission-review-detail";

export const metadata: Metadata = {
  title: "Review Submission — HFX Connect",
};

interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function ModerationResourceSubmissionDetailPage({ params }: PageProps) {
  const { id } = await params;
  return (
    <ModerationRoute>
      <ResourceSubmissionReviewDetail submissionId={id} />
    </ModerationRoute>
  );
}
