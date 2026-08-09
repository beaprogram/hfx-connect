import type { Metadata } from "next";
import { OrganizationRoute } from "@/components/organization/organization-route";
import { OrganizationClaimsList } from "@/components/organization/organization-claims-list";

export const metadata: Metadata = {
  title: "Claims — Organization — HFX Connect",
};

export default function OrganizationClaimsPage() {
  return (
    <OrganizationRoute>
      <OrganizationClaimsList />
    </OrganizationRoute>
  );
}
