import type { Metadata } from "next";
import { ProtectedRoute } from "@/components/auth/protected-route";
import { SubmitResourceForm } from "@/components/contributions/submit-resource-form";

export const metadata: Metadata = {
  title: "Propose a Resource — HFX Connect",
};

export default function SubmitResourcePage() {
  return (
    <ProtectedRoute>
      <SubmitResourceForm />
    </ProtectedRoute>
  );
}
