import type { Metadata } from "next";
import { OrganizationRoute } from "@/components/organization/organization-route";
import { OrganizationResourcesList } from "@/components/organization/organization-resources-list";

export const metadata: Metadata = {
  title: "Resources — Organization — HFX Connect",
};

export default function OrganizationResourcesPage() {
  return (
    <OrganizationRoute>
      <OrganizationResourcesList />
    </OrganizationRoute>
  );
}
