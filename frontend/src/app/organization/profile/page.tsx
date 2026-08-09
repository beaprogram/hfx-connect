import type { Metadata } from "next";
import { OrganizationRoute } from "@/components/organization/organization-route";
import { OrganizationProfilePage } from "@/components/organization/organization-profile-page";

export const metadata: Metadata = {
  title: "Edit Profile — Organization — HFX Connect",
};

export default function OrganizationProfileRoutePage() {
  return (
    <OrganizationRoute>
      <OrganizationProfilePage />
    </OrganizationRoute>
  );
}
