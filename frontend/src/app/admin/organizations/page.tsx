import type { Metadata } from "next";
import { AdminRoute } from "@/components/organization/admin-route";
import { AdminOrganizationQueue } from "@/components/organization/admin-organization-queue";

export const metadata: Metadata = {
  title: "Organizations — Administration — HFX Connect",
};

export default function AdminOrganizationsPage() {
  return (
    <AdminRoute>
      <AdminOrganizationQueue />
    </AdminRoute>
  );
}
