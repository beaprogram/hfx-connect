import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getResourceBySlug } from "@/lib/api/resources";
import { ApiRequestError } from "@/lib/api/errors";
import { ProtectedRoute } from "@/components/auth/protected-route";
import { CorrectionReportForm } from "@/components/contributions/correction-report-form";

interface PageProps {
  params: Promise<{ slug: string }>;
}

export async function generateMetadata({ params }: PageProps): Promise<Metadata> {
  const { slug } = await params;
  try {
    const resource = await getResourceBySlug(slug);
    return { title: `Report an issue — ${resource.name} | HFX Connect` };
  } catch {
    return { title: "Report an issue | HFX Connect" };
  }
}

export default async function ReportResourcePage({ params }: PageProps) {
  const { slug } = await params;
  let resource;
  try {
    resource = await getResourceBySlug(slug);
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 404) {
      notFound();
    }
    throw error;
  }

  return (
    <ProtectedRoute>
      <CorrectionReportForm resourceId={resource.id} resourceName={resource.name} />
    </ProtectedRoute>
  );
}
