import { QueryClient, isServer } from "@tanstack/react-query";
import { isAbortError } from "@/lib/api/errors";

function makeQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Public directory data — fine to treat as fresh for a short window
        // rather than refetching on every focus/mount.
        staleTime: 30 * 1000,
        // A cancelled request (abandoned filter change) shouldn't count as a
        // retryable failure; a genuine failure gets one retry, not endless
        // ones (see the milestone's "no infinite retry loops" requirement).
        retry: (failureCount, error) => !isAbortError(error) && failureCount < 1,
      },
    },
  });
}

// One QueryClient per server request (a shared instance would leak state
// between different users' requests), but a single persistent instance in
// the browser so client-side cache/navigation behaves as expected — the
// standard pattern for TanStack Query with the Next.js App Router.
let browserQueryClient: QueryClient | undefined;

export function getQueryClient(): QueryClient {
  if (isServer) {
    return makeQueryClient();
  }
  if (!browserQueryClient) {
    browserQueryClient = makeQueryClient();
  }
  return browserQueryClient;
}
