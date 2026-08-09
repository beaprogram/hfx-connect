import type { Metadata } from "next";
import { AdminRoute } from "@/components/organization/admin-route";
import { AdminOrganizationDetail } from "@/components/organization/admin-organization-detail";

export const metadata: Metadata = {
  title: "Review Organization — HFX Connect",
};

interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function AdminOrganizationDetailPage({ params }: PageProps) {
  const { id } = await params;
  return (
    <AdminRoute>
      <AdminOrganizationDetail organizationId={id} />
    </AdminRoute>
  );
}
