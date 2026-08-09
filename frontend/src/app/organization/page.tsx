import type { Metadata } from "next";
import { OrganizationRoute } from "@/components/organization/organization-route";
import { OrganizationDashboard } from "@/components/organization/organization-dashboard";

export const metadata: Metadata = {
  title: "Organization — HFX Connect",
};

export default function OrganizationPage() {
  return (
    <OrganizationRoute>
      <OrganizationDashboard />
    </OrganizationRoute>
  );
}
