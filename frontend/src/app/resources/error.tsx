"use client";

import { RouteError } from "@/components/feedback/route-error";

export default function ResourcesError({
  error,
  unstable_retry,
}: {
  error: Error & { digest?: string };
  unstable_retry: () => void;
}) {
  return <RouteError error={error} onRetry={unstable_retry} />;
}
