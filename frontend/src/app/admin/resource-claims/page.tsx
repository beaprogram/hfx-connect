import type { Metadata } from "next";
import { AdminRoute } from "@/components/organization/admin-route";
import { AdminOwnershipClaimQueue } from "@/components/organization/admin-ownership-claim-queue";

export const metadata: Metadata = {
  title: "Resource Claims — Administration — HFX Connect",
};

export default function AdminResourceClaimsPage() {
  return (
    <AdminRoute>
      <AdminOwnershipClaimQueue />
    </AdminRoute>
  );
}
