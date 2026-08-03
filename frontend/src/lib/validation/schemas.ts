import { z } from "zod";

/**
 * Schemas mirror the real backend contract exactly, verified against the live
 * `/v3/api-docs` document (not written from memory) before this file existed —
 * see docs/architecture/frontend-architecture.md. Nothing here is invented:
 * notably, `ResourceResponseSchema` has no `accessibility` or `lastVerifiedAt`
 * field because the backend does not return one (see the resource-detail
 * wireframe's "A Note on Scope").
 *
 * Optional business fields use `.nullish()` rather than `.nullable()`: Jackson
 * on the backend serializes absent optional fields as an explicit JSON `null`
 * (confirmed against a running backend), but `.nullish()` also tolerates a
 * missing key entirely, which is a strictly safer assumption for something
 * this frontend does not control.
 */

export const costTypeSchema = z.enum(["FREE", "LOW_COST", "PAID", "UNKNOWN"]);
export type CostType = z.infer<typeof costTypeSchema>;

export const verificationStatusSchema = z.enum(["UNVERIFIED", "VERIFIED"]);
export type VerificationStatus = z.infer<typeof verificationStatusSchema>;

/**
 * A resource's currently-calculated open status (Milestone 6B — see
 * ADR-011). UNKNOWN means the resource has no operating-hours schedule at
 * all, never that today's status couldn't be determined.
 */
export const hoursStatusSchema = z.enum(["OPEN", "CLOSED", "UNKNOWN"]);
export type HoursStatus = z.infer<typeof hoursStatusSchema>;

export const dayOfWeekSchema = z.enum([
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
]);
export type DayOfWeek = z.infer<typeof dayOfWeekSchema>;

/**
 * opensAt/closesAt are ISO local-time strings with seconds ("09:00:00"),
 * confirmed against the real backend (Jackson's default JSR-310
 * LocalTime serialization, not LocalTime#toString()'s zero-seconds-omitted
 * form) — see ResourceApiIntegrationTest#operatingHoursTimesSerializeAsIsoLocalTimeWithSeconds.
 * Both are null for a closed day.
 */
export const operatingHoursEntrySchema = z.object({
  dayOfWeek: dayOfWeekSchema,
  closed: z.boolean(),
  opensAt: z.string().nullish(),
  closesAt: z.string().nullish(),
  overnight: z.boolean(),
});
export type OperatingHoursEntry = z.infer<typeof operatingHoursEntrySchema>;

export const operatingHoursSchema = z.object({
  timezone: z.string(),
  weeklyHours: z.array(operatingHoursEntrySchema),
  hoursStatus: hoursStatusSchema,
  openNow: z.boolean().nullish(),
});
export type OperatingHours = z.infer<typeof operatingHoursSchema>;

export const categorySummarySchema = z.object({
  id: z.number(),
  name: z.string(),
  slug: z.string(),
});
export type CategorySummary = z.infer<typeof categorySummarySchema>;

export const categoryResponseSchema = z.object({
  id: z.number(),
  name: z.string(),
  slug: z.string(),
  description: z.string().nullish(),
  active: z.boolean(),
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type CategoryResponse = z.infer<typeof categoryResponseSchema>;

export const categoryPageResponseSchema = z.object({
  content: z.array(categoryResponseSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});
export type CategoryPageResponse = z.infer<typeof categoryPageResponseSchema>;

export const resourceSummaryResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  slug: z.string(),
  city: z.string().nullish(),
  province: z.string().nullish(),
  costType: costTypeSchema,
  verificationStatus: verificationStatusSchema,
  active: z.boolean(),
  category: categorySummarySchema,
  createdAt: z.string(),
  hoursStatus: hoursStatusSchema,
  openNow: z.boolean().nullish(),
});
export type ResourceSummaryResponse = z.infer<typeof resourceSummaryResponseSchema>;

export const resourceResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  slug: z.string(),
  description: z.string().nullish(),
  addressLine1: z.string().nullish(),
  addressLine2: z.string().nullish(),
  city: z.string().nullish(),
  province: z.string().nullish(),
  postalCode: z.string().nullish(),
  phone: z.string().nullish(),
  email: z.string().nullish(),
  websiteUrl: z.string().nullish(),
  costType: costTypeSchema,
  costDetails: z.string().nullish(),
  eligibility: z.string().nullish(),
  verificationStatus: verificationStatusSchema,
  active: z.boolean(),
  category: categorySummarySchema,
  createdAt: z.string(),
  updatedAt: z.string(),
  hours: operatingHoursSchema,
});
export type ResourceResponse = z.infer<typeof resourceResponseSchema>;

export const resourcePageResponseSchema = z.object({
  content: z.array(resourceSummaryResponseSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});
export type ResourcePageResponse = z.infer<typeof resourcePageResponseSchema>;

/**
 * A resource-summary card plus its geographic distance from the search
 * origin (Milestone 7B, consuming Milestone 7A's `GET /resources/nearby` —
 * see ADR-012/ADR-013). `latitude`/`longitude` are the resource's own saved
 * coordinate, never the caller's; `distanceMeters` is straight-line
 * ("as the crow flies") distance, never route distance or travel time.
 * Latitude/longitude/distance are constrained to finite, physically valid
 * ranges — a response with an out-of-range coordinate or a negative
 * distance fails validation and is never rendered as a marker.
 */
export const nearbyResourceSummaryResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  slug: z.string(),
  city: z.string().nullish(),
  province: z.string().nullish(),
  costType: costTypeSchema,
  verificationStatus: verificationStatusSchema,
  active: z.boolean(),
  category: categorySummarySchema,
  createdAt: z.string(),
  hoursStatus: hoursStatusSchema,
  openNow: z.boolean().nullish(),
  latitude: z.number().finite().gte(-90).lte(90),
  longitude: z.number().finite().gte(-180).lte(180),
  distanceMeters: z.number().finite().nonnegative(),
});
export type NearbyResourceSummaryResponse = z.infer<typeof nearbyResourceSummaryResponseSchema>;

export const nearbyResourcePageResponseSchema = z.object({
  content: z.array(nearbyResourceSummaryResponseSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});
export type NearbyResourcePageResponse = z.infer<typeof nearbyResourcePageResponseSchema>;

/**
 * One saved resource (Milestone 8A) — `savedAt` plus the same
 * `resourceSummaryResponseSchema` the plain public list already uses.
 * Deliberately has no `userId` field at all: the backend response never
 * includes one (every saved-resource endpoint is scoped to the current
 * authenticated principal), so there is nothing to accidentally trust from
 * a caller-supplied value here either.
 */
export const savedResourceSummaryResponseSchema = z.object({
  savedAt: z.string(),
  resource: resourceSummaryResponseSchema,
});
export type SavedResourceSummaryResponse = z.infer<typeof savedResourceSummaryResponseSchema>;

export const savedResourcePageResponseSchema = z.object({
  content: z.array(savedResourceSummaryResponseSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});
export type SavedResourcePageResponse = z.infer<typeof savedResourcePageResponseSchema>;

/** The batch saved-status lookup's response (Milestone 8A) — only the current user's own saved subset of the requested ids, never another user's data. */
export const savedResourceStatusResponseSchema = z.object({
  savedResourceIds: z.array(z.string()),
});
export type SavedResourceStatusResponse = z.infer<typeof savedResourceStatusResponseSchema>;

export const roleSchema = z.enum(["USER", "ORGANIZATION", "MODERATOR", "ADMIN"]);
export type Role = z.infer<typeof roleSchema>;

export const accountStatusSchema = z.enum(["ACTIVE", "PENDING_VERIFICATION", "SUSPENDED", "DEACTIVATED"]);
export type AccountStatus = z.infer<typeof accountStatusSchema>;

export const userResponseSchema = z.object({
  id: z.string(),
  email: z.string(),
  role: roleSchema,
  status: accountStatusSchema,
  emailVerified: z.boolean(),
  createdAt: z.string(),
});
export type UserResponse = z.infer<typeof userResponseSchema>;

/**
 * The access token is a JWT string, but nothing on the frontend decodes or
 * trusts its claims — `expiresIn` (seconds from issuance) is the one number
 * the auth provider actually tracks, per ADR-009's "Access-Token Refresh
 * Behavior" and "Token Expiry Handling" sections.
 */
export const loginResponseSchema = z.object({
  accessToken: z.string(),
  tokenType: z.string(),
  expiresIn: z.number(),
  user: userResponseSchema,
});
export type LoginResponse = z.infer<typeof loginResponseSchema>;

export const apiErrorSchema = z.object({
  timestamp: z.string().nullish(),
  status: z.number(),
  code: z.string(),
  message: z.string(),
  fieldErrors: z.record(z.string(), z.string()).nullish(),
});
export type ApiErrorBody = z.infer<typeof apiErrorSchema>;
