package com.hfxconnect.savedresource;

import com.hfxconnect.category.Category;
import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.InvalidSortException;
import com.hfxconnect.resource.CategorySummaryResponse;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.OpenNowCalculator;
import com.hfxconnect.resource.OperatingHoursEntry;
import com.hfxconnect.resource.ResourceNotFoundException;
import com.hfxconnect.resource.ResourceOperatingHours;
import com.hfxconnect.resource.ResourceOperatingHoursRepository;
import com.hfxconnect.resource.ResourceRepository;
import com.hfxconnect.resource.ResourceSummaryResponse;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All saved-resource business logic (Milestone 8A): idempotent save/remove,
 * paginated active-only listing, and the batch saved-status lookup. Every
 * method here is scoped by a {@code userId} the caller (
 * {@code SavedResourceController}) derives exclusively from the
 * authenticated {@code CurrentUserPrincipal} — nothing in this class ever
 * accepts a user id as request data, and nothing here trusts a caller-
 * supplied identity.
 *
 * <p>{@code JwtAuthenticationFilter} already re-loads the account and
 * confirms it is {@code ACTIVE} before any request reaches this service (see
 * ADR-009) — a suspended/deactivated account is rejected as unauthenticated
 * before {@code userId} ever exists as a trusted value here, so this service
 * does not re-check account status itself.
 */
@Service
public class SavedResourceService {

	static final int MAX_PAGE_SIZE = 100;

	private static final Set<String> ALLOWED_SORT_VALUES = Set.of("savedAt", "name");

	private final SavedResourceRepository savedResourceRepository;
	private final ResourceRepository resourceRepository;
	private final ResourceOperatingHoursRepository operatingHoursRepository;
	private final OpenNowCalculator openNowCalculator;

	public SavedResourceService(SavedResourceRepository savedResourceRepository, ResourceRepository resourceRepository,
			ResourceOperatingHoursRepository operatingHoursRepository, OpenNowCalculator openNowCalculator) {
		this.savedResourceRepository = savedResourceRepository;
		this.resourceRepository = resourceRepository;
		this.operatingHoursRepository = operatingHoursRepository;
		this.openNowCalculator = openNowCalculator;
	}

	/**
	 * Ensures {@code resourceId} is saved for {@code userId} — idempotent: a
	 * repeated call for an already-saved resource succeeds without creating a
	 * duplicate row. The resource must currently be active, exactly like
	 * every other public resource visibility rule in this codebase; a
	 * missing or inactive resource is indistinguishable from the caller's
	 * point of view (both throw {@link ResourceNotFoundException}) so an
	 * inactive resource's existence is never revealed through a different
	 * error.
	 */
	@Transactional
	public void save(UUID userId, UUID resourceId) {
		if (savedResourceRepository.existsByUserIdAndResource_Id(userId, resourceId)) {
			return;
		}

		CommunityResource resource = resourceRepository.findById(resourceId)
				.filter(CommunityResource::isActive)
				.orElseThrow(() -> ResourceNotFoundException.byId(resourceId));

		try {
			// saveAndFlush: forces the insert (and any constraint violation)
			// to happen synchronously in this try block, the same reasoning
			// ResourceService.create applies to its own duplicate-slug race.
			savedResourceRepository.saveAndFlush(new SavedResource(userId, resource));
		} catch (DataIntegrityViolationException ex) {
			// A concurrent request already inserted the identical
			// (userId, resourceId) row between the exists() check above and
			// this insert. The desired end state — this resource is saved —
			// is already true, so this is a successful outcome, not a
			// conflict: the database's own unique constraint
			// (saved_resources_user_resource_key, V8) is what actually
			// guarantees at most one row, this catch just keeps the race
			// from surfacing as an unexpected 500.
		}
	}

	/**
	 * Ensures {@code resourceId} is no longer saved for {@code userId} —
	 * idempotent: removing an absent relation is a successful no-op, not an
	 * error. Deliberately does not require the resource to currently be
	 * active or even exist — a saved relation the user owns can always be
	 * removed by resource id regardless of that resource's current public
	 * visibility (see the milestone's "Resource Visibility Rules"). Operates
	 * only on {@code userId}'s own relation; there is no code path that could
	 * ever touch another user's row, since the delete is scoped by both
	 * {@code userId} and {@code resourceId} together.
	 */
	@Transactional
	public void remove(UUID userId, UUID resourceId) {
		savedResourceRepository.deleteByUserIdAndResource_Id(userId, resourceId);
	}

	/**
	 * Lists {@code userId}'s saved, currently-active resources, newest-saved
	 * first by default. Mirrors {@code ResourceService.search}'s batch
	 * operating-hours strategy exactly: one page query (already
	 * {@code JOIN FETCH}-ing the resource and its category), one batch hours
	 * query for the page's resource ids, one Halifax "now" computed once and
	 * reused for every resource on the page.
	 */
	@Transactional(readOnly = true)
	public SavedResourcePageResponse list(UUID userId, int page, int size, String sort) {
		Pageable pageable = pageable(page, size);
		Page<SavedResource> results = "name".equals(resolveSort(sort))
				? savedResourceRepository.findActiveSavedResourcesByUserIdOrderByResourceName(userId, pageable)
				: savedResourceRepository.findActiveSavedResourcesByUserIdOrderBySavedAtDesc(userId, pageable);

		List<UUID> resourceIds = results.getContent().stream().map(sr -> sr.getResource().getId()).toList();
		Map<UUID, List<OperatingHoursEntry>> hoursByResourceId = loadHoursByResourceIds(resourceIds);
		ZonedDateTime nowHalifax = openNowCalculator.nowInHalifax();

		List<SavedResourceSummaryResponse> content = results.getContent().stream()
				.map(savedResource -> toSummary(savedResource,
						hoursByResourceId.getOrDefault(savedResource.getResource().getId(), List.of()), nowHalifax))
				.toList();

		return new SavedResourcePageResponse(
				content, results.getNumber(), results.getSize(), results.getTotalElements(), results.getTotalPages());
	}

	/**
	 * Returns which of {@code request}'s candidate resource ids
	 * {@code userId} has saved — the batch alternative to one status request
	 * per visible resource card. Reveals nothing about any other user's
	 * saved resources, and never distinguishes "not saved" from "doesn't
	 * exist"/"inactive" in its response (both are simply absent from the
	 * result).
	 */
	@Transactional(readOnly = true)
	public SavedResourceStatusResponse status(UUID userId, SavedResourceStatusRequest request) {
		SavedResourceValidation.Normalized normalized = SavedResourceValidation.validateStatusRequest(request);
		if (normalized.resourceIds().isEmpty()) {
			return new SavedResourceStatusResponse(List.of());
		}
		List<UUID> savedResourceIds = savedResourceRepository.findSavedResourceIds(userId, normalized.resourceIds());
		return new SavedResourceStatusResponse(savedResourceIds);
	}

	private SavedResourceSummaryResponse toSummary(
			SavedResource savedResource, List<OperatingHoursEntry> hours, ZonedDateTime nowHalifax) {
		CommunityResource resource = savedResource.getResource();
		Category category = resource.getCategory();
		OpenNowCalculator.Result result = openNowCalculator.calculate(hours, nowHalifax);

		ResourceSummaryResponse summary = new ResourceSummaryResponse(
				resource.getId(),
				resource.getName(),
				resource.getSlug(),
				resource.getCity(),
				resource.getProvince(),
				resource.getCostType(),
				resource.getVerificationStatus(),
				resource.isActive(),
				new CategorySummaryResponse(category.getId(), category.getName(), category.getSlug()),
				resource.getCreatedAt(),
				result.status(),
				result.openNow());

		return new SavedResourceSummaryResponse(savedResource.getCreatedAt(), summary);
	}

	private Map<UUID, List<OperatingHoursEntry>> loadHoursByResourceIds(List<UUID> resourceIds) {
		if (resourceIds.isEmpty()) {
			return Map.of();
		}
		return operatingHoursRepository.findByResourceIdIn(resourceIds).stream()
				.collect(Collectors.groupingBy(ResourceOperatingHours::getResourceId,
						Collectors.mapping(
								entity -> new OperatingHoursEntry(
										entity.getDayOfWeek(), entity.isClosed(), entity.getOpensAt(), entity.getClosesAt()),
								Collectors.toList())));
	}

	private static Pageable pageable(int page, int size) {
		if (page < 0) {
			throw new InvalidPaginationException("page must not be negative.");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidPaginationException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
		}
		// No Sort attached here: sort selects which of the two fixed-ORDER-BY
		// repository queries to call (see SavedResourceRepository), not a
		// dynamic Sort appended to a shared query.
		return PageRequest.of(page, size);
	}

	/** {@code savedAt} (the default) or {@code name} — anything else is rejected before any query runs. */
	private static String resolveSort(String sort) {
		if (sort == null || sort.isBlank() || "savedAt".equals(sort)) {
			return "savedAt";
		}
		if ("name".equals(sort)) {
			return "name";
		}
		throw new InvalidSortException("sort must be one of " + ALLOWED_SORT_VALUES + " (got '" + sort + "').");
	}

}
