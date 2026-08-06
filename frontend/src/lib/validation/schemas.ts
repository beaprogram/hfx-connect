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
  /** Set once a moderator verifies this resource (Milestone 9A) — null otherwise. */
  lastVerifiedAt: z.string().nullish(),
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

/**
 * Contribution review states (Milestone 8B) — shared shape for both
 * resource submissions and correction reports. `APPROVED`/`REJECTED` exist
 * in the schema for Milestone 9 compatibility; a user-created item is
 * always `PENDING_REVIEW` until it is withdrawn.
 */
export const contributionStatusSchema = z.enum(["PENDING_REVIEW", "APPROVED", "REJECTED", "WITHDRAWN"]);
export type ContributionStatus = z.infer<typeof contributionStatusSchema>;

/**
 * The public resource an approved submission created (Milestone 9A) — shown
 * to both the submission's owner and, in the moderation UI, to reviewers.
 */
export const resultingResourceSummarySchema = z.object({
  id: z.string(),
  name: z.string(),
  slug: z.string(),
});
export type ResultingResourceSummary = z.infer<typeof resultingResourceSummarySchema>;

/**
 * A proposed new resource (Milestone 8B), owned by the current user.
 * Deliberately has no `submittedByUserId` field — the backend response
 * never includes one, matching `savedResourceSummaryResponseSchema`'s same
 * reasoning. `reviewedAt`/`reviewReason`/`resultingResource` are populated
 * once a moderator has decided (Milestone 9A) — the reviewer's identity is
 * never included in this owner-facing shape.
 */
export const resourceSubmissionResponseSchema = z.object({
  id: z.string(),
  category: categorySummarySchema,
  name: z.string(),
  shortDescription: z.string(),
  fullDescription: z.string().nullish(),
  addressLine1: z.string(),
  addressLine2: z.string().nullish(),
  city: z.string(),
  province: z.string(),
  postalCode: z.string(),
  phone: z.string().nullish(),
  email: z.string().nullish(),
  websiteUrl: z.string().nullish(),
  costType: costTypeSchema,
  eligibilityInformation: z.string().nullish(),
  accessibilityInformation: z.string().nullish(),
  status: contributionStatusSchema,
  submittedAt: z.string(),
  updatedAt: z.string(),
  withdrawnAt: z.string().nullish(),
  reviewedAt: z.string().nullish(),
  reviewReason: z.string().nullish(),
  resultingResource: resultingResourceSummarySchema.nullish(),
});
export type ResourceSubmissionResponse = z.infer<typeof resourceSubmissionResponseSchema>;

export const resourceSubmissionPageResponseSchema = z.object({
  content: z.array(resourceSubmissionResponseSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});
export type ResourceSubmissionPageResponse = z.infer<typeof resourceSubmissionPageResponseSchema>;

export const issueTypeSchema = z.enum([
  "GENERAL_INFORMATION",
  "ADDRESS",
  "CONTACT_INFORMATION",
  "OPERATING_HOURS",
  "ELIGIBILITY",
  "ACCESSIBILITY",
  "COST",
  "RESOURCE_CLOSED",
  "DUPLICATE_RESOURCE",
  "OTHER",
]);
export type IssueType = z.infer<typeof issueTypeSchema>;

/**
 * The resource a correction report is about (Milestone 8B). `name`/`slug`
 * come from a snapshot captured when the report was created, not a live
 * read — they stay meaningful even if the resource is later deleted, in
 * which case `resourceId` is `null`.
 */
export const correctionReportTargetResponseSchema = z.object({
  resourceId: z.string().nullish(),
  name: z.string(),
  slug: z.string(),
});
export type CorrectionReportTargetResponse = z.infer<typeof correctionReportTargetResponseSchema>;

/**
 * `changesApplied` is `null` while pending/withdrawn, and only meaningful
 * once `status` is `APPROVED` (Milestone 9A) — a moderator may approve a
 * report without applying any automatic field change (see ADR-016).
 */
export const correctionReportResponseSchema = z.object({
  id: z.string(),
  resource: correctionReportTargetResponseSchema,
  issueType: issueTypeSchema,
  explanation: z.string(),
  proposedName: z.string().nullish(),
  proposedDescription: z.string().nullish(),
  proposedAddressLine1: z.string().nullish(),
  proposedAddressLine2: z.string().nullish(),
  proposedCity: z.string().nullish(),
  proposedProvince: z.string().nullish(),
  proposedPostalCode: z.string().nullish(),
  proposedPhone: z.string().nullish(),
  proposedEmail: z.string().nullish(),
  proposedWebsiteUrl: z.string().nullish(),
  proposedCostType: costTypeSchema.nullish(),
  proposedCostDetails: z.string().nullish(),
  proposedEligibility: z.string().nullish(),
  status: contributionStatusSchema,
  submittedAt: z.string(),
  updatedAt: z.string(),
  reviewedAt: z.string().nullish(),
  reviewReason: z.string().nullish(),
  changesApplied: z.boolean().nullish(),
  withdrawnAt: z.string().nullish(),
});
export type CorrectionReportResponse = z.infer<typeof correctionReportResponseSchema>;

export const correctionReportPageResponseSchema = z.object({
  content: z.array(correctionReportResponseSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});
export type CorrectionReportPageResponse = z.infer<typeof correctionReportPageResponseSchema>;

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

/**
 * Moderation (Milestone 9A). Every schema below is used only by
 * MODERATOR/ADMIN-facing routes — see `lib/api/moderation.ts` and
 * `components/moderation/`. Submitter/reporter identity is deliberately
 * never included in any queue or detail shape (see ADR-016's "Submitter
 * Identity" section) — only the reviewing moderator's account id
 * (`reviewedByUserId`) appears, and only after a decision has been made.
 */

export const moderationSubmissionQueueItemSchema = z.object({
  id: z.string(),
  category: categorySummarySchema,
  name: z.string(),
  shortDescription: z.string(),
  status: contributionStatusSchema,
  submittedAt: z.string(),
});
export type ModerationSubmissionQueueItem = z.infer<typeof moderationSubmissionQueueItemSchema>;

export const moderationSubmissionQueuePageSchema = z.object({
  content: z.array(moderationSubmissionQueueItemSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});
export type ModerationSubmissionQueuePage = z.infer<typeof moderationSubmissionQueuePageSchema>;

export const moderationSubmissionDetailSchema = z.object({
  id: z.string(),
  category: categorySummarySchema,
  name: z.string(),
  shortDescription: z.string(),
  fullDescription: z.string().nullish(),
  addressLine1: z.string(),
  addressLine2: z.string().nullish(),
  city: z.string(),
  province: z.string(),
  postalCode: z.string(),
  phone: z.string().nullish(),
  email: z.string().nullish(),
  websiteUrl: z.string().nullish(),
  costType: costTypeSchema,
  eligibilityInformation: z.string().nullish(),
  accessibilityInformation: z.string().nullish(),
  status: contributionStatusSchema,
  submittedAt: z.string(),
  updatedAt: z.string(),
  withdrawnAt: z.string().nullish(),
  reviewedByUserId: z.string().nullish(),
  reviewedAt: z.string().nullish(),
  reviewReason: z.string().nullish(),
  resultingResource: resultingResourceSummarySchema.nullish(),
});
export type ModerationSubmissionDetail = z.infer<typeof moderationSubmissionDetailSchema>;

export const moderationCorrectionQueueItemSchema = z.object({
  id: z.string(),
  resource: correctionReportTargetResponseSchema,
  issueType: issueTypeSchema,
  explanationPreview: z.string(),
  status: contributionStatusSchema,
  submittedAt: z.string(),
});
export type ModerationCorrectionQueueItem = z.infer<typeof moderationCorrectionQueueItemSchema>;

export const moderationCorrectionQueuePageSchema = z.object({
  content: z.array(moderationCorrectionQueueItemSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});
export type ModerationCorrectionQueuePage = z.infer<typeof moderationCorrectionQueuePageSchema>;

/** The target resource's current live values — `null` when the resource has since been deleted. */
export const currentResourceStateSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
  addressLine1: z.string(),
  addressLine2: z.string().nullish(),
  city: z.string(),
  province: z.string(),
  postalCode: z.string(),
  phone: z.string().nullish(),
  email: z.string().nullish(),
  websiteUrl: z.string().nullish(),
  costType: costTypeSchema,
  costDetails: z.string().nullish(),
  eligibility: z.string().nullish(),
  active: z.boolean(),
});
export type CurrentResourceState = z.infer<typeof currentResourceStateSchema>;

export const moderationCorrectionDetailSchema = z.object({
  id: z.string(),
  resource: correctionReportTargetResponseSchema,
  currentResource: currentResourceStateSchema.nullish(),
  issueType: issueTypeSchema,
  explanation: z.string(),
  proposedName: z.string().nullish(),
  proposedDescription: z.string().nullish(),
  proposedAddressLine1: z.string().nullish(),
  proposedAddressLine2: z.string().nullish(),
  proposedCity: z.string().nullish(),
  proposedProvince: z.string().nullish(),
  proposedPostalCode: z.string().nullish(),
  proposedPhone: z.string().nullish(),
  proposedEmail: z.string().nullish(),
  proposedWebsiteUrl: z.string().nullish(),
  proposedCostType: costTypeSchema.nullish(),
  proposedCostDetails: z.string().nullish(),
  proposedEligibility: z.string().nullish(),
  status: contributionStatusSchema,
  submittedAt: z.string(),
  updatedAt: z.string(),
  withdrawnAt: z.string().nullish(),
  reviewedByUserId: z.string().nullish(),
  reviewedAt: z.string().nullish(),
  reviewReason: z.string().nullish(),
  appliedToResourceAt: z.string().nullish(),
});
export type ModerationCorrectionDetail = z.infer<typeof moderationCorrectionDetailSchema>;

export const contributionTypeSchema = z.enum(["RESOURCE_SUBMISSION", "CORRECTION_REPORT"]);
export type ContributionType = z.infer<typeof contributionTypeSchema>;

export const moderationActionSchema = z.enum([
  "REVIEW_DECISION",
  "RESOURCE_CREATED",
  "RESOURCE_UPDATED",
  "RESOURCE_DEACTIVATED",
]);
export type ModerationAction = z.infer<typeof moderationActionSchema>;

export const moderationDecisionSchema = z.enum(["APPROVED", "REJECTED"]);
export type ModerationDecision = z.infer<typeof moderationDecisionSchema>;

/**
 * Moderator/admin-only audit trail entry (Milestone 9A) — never fetched by
 * any owner-facing or public route. `beforeSnapshot`/`afterSnapshot` are
 * small, explicitly-built field maps the backend constructs (never a
 * serialized entity) — treated here as an opaque record for display, not
 * something the frontend interprets structurally.
 */
export const moderationAuditEventSchema = z.object({
  id: z.string(),
  contributionType: contributionTypeSchema,
  contributionId: z.string(),
  action: moderationActionSchema,
  decision: moderationDecisionSchema.nullish(),
  actorUserId: z.string(),
  actorEmail: z.string(),
  reviewReason: z.string().nullish(),
  affectedResourceId: z.string().nullish(),
  beforeSnapshot: z.record(z.string(), z.unknown()).nullish(),
  afterSnapshot: z.record(z.string(), z.unknown()).nullish(),
  createdAt: z.string(),
});
export type ModerationAuditEvent = z.infer<typeof moderationAuditEventSchema>;

export const moderationAuditEventPageSchema = z.object({
  content: z.array(moderationAuditEventSchema),
  page: z.number(),
  size: z.number(),
  totalElements: z.number(),
  totalPages: z.number(),
});
export type ModerationAuditEventPage = z.infer<typeof moderationAuditEventPageSchema>;
