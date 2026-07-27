import type { Metadata } from "next";
import { ProtectedRoute } from "@/components/auth/protected-route";
import { DashboardContent } from "@/components/auth/dashboard-content";

export const metadata: Metadata = {
  title: "Dashboard — HFX Connect",
};

export default function DashboardPage() {
  return (
    <ProtectedRoute>
      <DashboardContent />
    </ProtectedRoute>
  );
}
