import type { Metadata } from "next";
import { ModerationRoute } from "@/components/moderation/moderation-route";
import { ModerationTabs } from "@/components/moderation/moderation-tabs";

export const metadata: Metadata = {
  title: "Moderation — HFX Connect",
};

export default function ModerationPage() {
  return (
    <ModerationRoute>
      <ModerationTabs />
    </ModerationRoute>
  );
}
