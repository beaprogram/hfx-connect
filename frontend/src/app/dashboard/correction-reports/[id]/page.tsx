import type { Metadata } from "next";
import { ProtectedRoute } from "@/components/auth/protected-route";
import { CorrectionReportDetail } from "@/components/contributions/correction-report-detail";

export const metadata: Metadata = {
  title: "Your Correction Report — HFX Connect",
};

interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function CorrectionReportDetailPage({ params }: PageProps) {
  const { id } = await params;
  return (
    <ProtectedRoute>
      <CorrectionReportDetail reportId={id} />
    </ProtectedRoute>
  );
}
