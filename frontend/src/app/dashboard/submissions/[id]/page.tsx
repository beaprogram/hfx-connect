import type { Metadata } from "next";
import { ProtectedRoute } from "@/components/auth/protected-route";
import { ResourceSubmissionDetail } from "@/components/contributions/resource-submission-detail";

export const metadata: Metadata = {
  title: "Your Submission — HFX Connect",
};

interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function SubmissionDetailPage({ params }: PageProps) {
  const { id } = await params;
  return (
    <ProtectedRoute>
      <ResourceSubmissionDetail submissionId={id} />
    </ProtectedRoute>
  );
}
