import type { Metadata } from "next";
import { AdminRoute } from "@/components/organization/admin-route";
import { AdminOwnershipClaimDetail } from "@/components/organization/admin-ownership-claim-detail";

export const metadata: Metadata = {
  title: "Review Claim — HFX Connect",
};

interface PageProps {
  params: Promise<{ id: string }>;
}

export default async function AdminResourceClaimDetailPage({ params }: PageProps) {
  const { id } = await params;
  return (
    <AdminRoute>
      <AdminOwnershipClaimDetail claimId={id} />
    </AdminRoute>
  );
}
