"use client";

import { QueryClientProvider } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { getQueryClient } from "./get-query-client";

export function QueryProvider({ children }: { children: ReactNode }) {
  // Calling getQueryClient() directly (not in useState) is intentional and
  // safe here: on the server it always makes a fresh client per request; in
  // the browser it returns the same singleton every render.
  const queryClient = getQueryClient();

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}
