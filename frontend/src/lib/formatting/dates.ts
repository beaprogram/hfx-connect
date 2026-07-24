/**
 * Explicit locale and time zone (rather than the runtime's defaults) so this
 * produces the same string on the server during rendering and on the client
 * during hydration, regardless of either machine's own locale/time zone
 * settings — avoiding a React hydration mismatch.
 */
const DATE_FORMATTER = new Intl.DateTimeFormat("en-CA", {
  dateStyle: "medium",
  timeZone: "America/Halifax",
});

export function formatDate(isoTimestamp: string): string {
  return DATE_FORMATTER.format(new Date(isoTimestamp));
}
