package com.hfxconnect.resource;

import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.error.InvalidCostTypeException;
import com.hfxconnect.common.error.InvalidLatitudeException;
import com.hfxconnect.common.error.InvalidLongitudeException;
import com.hfxconnect.common.error.InvalidOpenNowFilterException;
import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.InvalidRadiusException;
import com.hfxconnect.common.error.InvalidSortException;
import com.hfxconnect.common.error.InvalidVerificationStatusException;
import com.hfxconnect.common.error.ValidationException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All resource business logic: category validation, field normalization,
 * slug generation, duplicate detection, transaction boundaries, and mapping
 * to/from the business-layer read models. See
 * {@code docs/architecture/backend-architecture.md} for the layering pattern
 * this follows (the same one {@code CategoryService} established).
 *
 * <p>Public read methods ({@link #getActiveById}, {@link #getActiveBySlug},
 * {@link #search}) only ever see active resources — deactivated resources
 * are treated as not found, the same "public-style" visibility rule
 * {@code ResourceController} relies on.
 */
@Service
public class ResourceService {

	static final int MAX_PAGE_SIZE = 100;

	/**
	 * Nearby-search radius defaults/limits (Milestone 7A — see ADR-012's
	 * "Radius" section). Deliberately Halifax-scoped: 5 km default covers
	 * most of the urban core from a central point; 50 km comfortably covers
	 * the whole Halifax Regional Municipality without an effectively-
	 * unbounded search. Coordinates themselves are never restricted to a
	 * Halifax bounding box — only the radius is.
	 */
	static final double DEFAULT_RADIUS_KM = 5.0;
	static final double MAX_RADIUS_KM = 50.0;
	private static final double METRES_PER_KILOMETRE = 1000.0;

	/**
	 * Allowlisted values for the public {@code sort} query parameter — see
	 * {@code docs/api/README.md}. Arbitrary entity-field sorting is
	 * deliberately not permitted (it would leak internal field names into the
	 * public contract and let callers request expensive sorts with no index
	 * support).
	 */
	private static final Set<String> ALLOWED_SORT_VALUES = Set.of("name", "createdAt");

	private final ResourceRepository resourceRepository;
	private final CategoryRepository categoryRepository;
	private final ResourceOperatingHoursRepository operatingHoursRepository;
	private final OpenNowCalculator openNowCalculator;

	public ResourceService(ResourceRepository resourceRepository, CategoryRepository categoryRepository,
			ResourceOperatingHoursRepository operatingHoursRepository, OpenNowCalculator openNowCalculator) {
		this.resourceRepository = resourceRepository;
		this.categoryRepository = categoryRepository;
		this.operatingHoursRepository = operatingHoursRepository;
		this.openNowCalculator = openNowCalculator;
	}

	@Transactional
	public ResourceDetails create(CreateResourceCommand command) {
		Category category = requireActiveCategory(command.categoryId());

		ResourceValidation.Normalized fields = ResourceValidation.validate(
				command.name(), command.description(), command.addressLine1(), command.addressLine2(),
				command.city(), command.province(), command.postalCode(), command.phone(), command.email(),
				command.websiteUrl(), command.costDetails(), command.eligibility());

		if (resourceRepository.existsBySlug(fields.slug())) {
			throw ResourceConflictException.duplicateSlug(fields.slug());
		}

		CostType costType = command.costType() != null ? command.costType() : CostType.UNKNOWN;

		CommunityResource resource = new CommunityResource(category, fields.name(), fields.slug(),
				fields.description(), fields.addressLine1(), fields.addressLine2(), fields.city(),
				fields.province(), fields.postalCode(), fields.phone(), fields.email(), fields.websiteUrl(),
				costType, fields.costDetails(), fields.eligibility());

		try {
			// saveAndFlush (not save): CommunityResource's id is a Hibernate-
			// generated UUID assigned in memory, unlike Category's IDENTITY
			// column, so a plain save() is not guaranteed to hit the database
			// synchronously — the INSERT could otherwise be deferred to the
			// next flush, which would happen after this try/catch has already
			// exited. Flushing explicitly is what makes the race-condition
			// catch below reliable.
			resource = resourceRepository.saveAndFlush(resource);
		} catch (DataIntegrityViolationException ex) {
			// Same reasoning as CategoryService.create(): the exists() check
			// above narrows the race window but does not close it.
			throw ResourceConflictException.duplicateSlug(fields.slug());
		}

		return ResourceDetails.from(resource);
	}

	@Transactional
	public ResourceDetails update(UUID id, UpdateResourceCommand command) {
		CommunityResource resource = findRequiredById(id);
		Category category = requireActiveCategory(command.categoryId());

		ResourceValidation.Normalized fields = ResourceValidation.validate(
				command.name(), command.description(), command.addressLine1(), command.addressLine2(),
				command.city(), command.province(), command.postalCode(), command.phone(), command.email(),
				command.websiteUrl(), command.costDetails(), command.eligibility());

		CostType costType = command.costType() != null ? command.costType() : CostType.UNKNOWN;

		// fields.slug() is intentionally discarded here — the stored slug
		// never changes after creation (see CommunityResource#updateDetails
		// and ADR-005's slug-stability reasoning). Running it through
		// ResourceValidation still matters: it proves the (possibly renamed)
		// resource name is not something that would produce an empty slug.
		resource.updateDetails(category, fields.name(), fields.description(), fields.addressLine1(),
				fields.addressLine2(), fields.city(), fields.province(), fields.postalCode(), fields.phone(),
				fields.email(), fields.websiteUrl(), costType, fields.costDetails(), fields.eligibility());

		return ResourceDetails.from(resource);
	}

	@Transactional
	public void deactivate(UUID id) {
		findRequiredById(id).deactivate();
	}

	@Transactional(readOnly = true)
	public ResourceDetails getActiveById(UUID id) {
		CommunityResource resource = resourceRepository.findByIdWithCategory(id)
				.filter(CommunityResource::isActive)
				.orElseThrow(() -> ResourceNotFoundException.byId(id));
		return toResourceDetailsWithHours(resource);
	}

	@Transactional(readOnly = true)
	public ResourceDetails getActiveBySlug(String slug) {
		CommunityResource resource = resourceRepository.findBySlugAndActiveTrueWithCategory(slug)
				.orElseThrow(() -> ResourceNotFoundException.bySlug(slug));
		return toResourceDetailsWithHours(resource);
	}

	/**
	 * The single public listing/search method (Milestone 6A, extended in 6B
	 * with {@code costType}/{@code verificationStatus}/{@code openNow}) —
	 * every filter is independently optional. See ADR-010 for why this
	 * replaced the previous {@code listActive}/{@code listActiveByCategory}
	 * pair, and ADR-011 for the open-now filtering/batch-loading strategy.
	 *
	 * <p>A blank/whitespace-only {@code query} is treated identically to a
	 * {@code null} one (no keyword filter) — {@link ResourceSearchQuery#normalize}
	 * makes that translation. An over-length query throws
	 * {@code InvalidSearchQueryException} (400) before any database query
	 * runs. {@code costType}/{@code verificationStatus} must exactly match an
	 * existing enum constant (case-insensitively); {@code openNow} must be
	 * {@code "true"}/{@code "false"} (or blank/absent, meaning no filter) —
	 * any other value throws the corresponding {@code Invalid*Exception}
	 * (400).
	 *
	 * <p>Halifax "now" is computed exactly once here and reused both for the
	 * database's {@code openNow} predicate and for every resource's
	 * {@link OpenNowCalculator} evaluation on this page, so every resource on
	 * one response is judged against the identical instant (ADR-011).
	 */
	@Transactional(readOnly = true)
	public ResourcePage search(String query, Long categoryId, String costType, String verificationStatus,
			String openNow, int page, int size, String sort) {
		String normalizedQuery = ResourceSearchQuery.normalize(query);
		String likePattern = normalizedQuery != null ? ResourceSearchQuery.toLikePattern(normalizedQuery) : null;
		CostType costTypeFilter = resolveCostType(costType);
		VerificationStatus verificationStatusFilter = resolveVerificationStatus(verificationStatus);
		boolean openNowOnly = resolveOpenNowFilter(openNow);

		ZonedDateTime nowHalifax = openNowCalculator.nowInHalifax();
		DayOfWeek today = nowHalifax.getDayOfWeek();
		DayOfWeek yesterday = today.minus(1);
		LocalTime now = nowHalifax.toLocalTime();

		var results = resourceRepository.search(categoryId, likePattern, costTypeFilter, verificationStatusFilter,
				openNowOnly, today, yesterday, now, pageable(page, size, sort));

		List<UUID> resourceIds = results.getContent().stream().map(CommunityResource::getId).toList();
		Map<UUID, List<OperatingHoursEntry>> hoursByResourceId = loadHoursByResourceIds(resourceIds);

		List<ResourceDetails> content = results.getContent().stream()
				.map(resource -> toResourceDetails(
						resource, hoursByResourceId.getOrDefault(resource.getId(), List.of()), nowHalifax))
				.toList();

		return new ResourcePage(content, results.getNumber(), results.getSize(), results.getTotalElements(),
				results.getTotalPages());
	}

	/**
	 * Fully replaces a resource's weekly schedule (delete-then-insert, one
	 * transaction) — see ADR-011's "Schedule Write Contract" section. Not
	 * public: {@code ResourceController} exposes this only to {@code ADMIN}/
	 * {@code MODERATOR} callers (see {@code SecurityConfig}).
	 */
	@Transactional
	public OperatingHoursResponse replaceOperatingHours(UUID id, ReplaceOperatingHoursRequest request) {
		findRequiredById(id);
		OperatingHoursValidation.Normalized normalized = OperatingHoursValidation.validate(request);

		operatingHoursRepository.deleteByResourceId(id);
		operatingHoursRepository.flush();

		List<ResourceOperatingHours> entities = normalized.entries().stream()
				.map(entry -> new ResourceOperatingHours(id, entry.dayOfWeek(), entry.closed(), entry.opensAt(),
						entry.closesAt()))
				.toList();
		operatingHoursRepository.saveAll(entities);

		ZonedDateTime nowHalifax = openNowCalculator.nowInHalifax();
		OpenNowCalculator.Result result = openNowCalculator.calculate(normalized.entries(), nowHalifax);
		return OperatingHoursResponse.from(normalized.entries(), result.status(), result.openNow());
	}

	/**
	 * Replaces a resource's geographic coordinate (Milestone 7A — see
	 * ADR-012). Not public: {@code ResourceController} exposes this only to
	 * {@code ADMIN}/{@code MODERATOR} callers. The response echoes back the
	 * just-validated input coordinates directly rather than re-querying —
	 * the write already proves what was stored, so a second round-trip
	 * would be redundant.
	 */
	@Transactional
	public ResourceLocationResponse replaceLocation(UUID id, ResourceLocationRequest request) {
		findRequiredById(id);
		ResourceLocationValidation.Normalized coordinates = ResourceLocationValidation.validate(request);

		Instant updatedAt = Instant.now();
		resourceRepository.updateLocation(id, coordinates.latitude(), coordinates.longitude(), updatedAt);

		return new ResourceLocationResponse(id, coordinates.latitude(), coordinates.longitude(), updatedAt);
	}

	/**
	 * Public nearby-resource search (Milestone 7A — see ADR-012). {@code
	 * latitude}/{@code longitude} are required; every other parameter is
	 * independently optional and combines exactly like {@link #search}'s
	 * filters (same keyword/cost/verification/open-now semantics, same
	 * active-only visibility, same batch-loaded operating hours). Ordered
	 * nearest-first — there is no separate {@code sort} parameter.
	 */
	@Transactional(readOnly = true)
	public NearbyResourcePageResponse nearby(Double latitude, Double longitude, Double radiusKm, String query,
			Long categoryId, String costType, String verificationStatus, String openNow, int page, int size) {
		requireLatitude(latitude);
		requireLongitude(longitude);
		double radiusMetres = resolveRadiusMetres(radiusKm);

		String normalizedQuery = ResourceSearchQuery.normalize(query);
		String likePattern = normalizedQuery != null ? ResourceSearchQuery.toLikePattern(normalizedQuery) : null;
		CostType costTypeFilter = resolveCostType(costType);
		VerificationStatus verificationStatusFilter = resolveVerificationStatus(verificationStatus);
		boolean openNowOnly = resolveOpenNowFilter(openNow);

		ZonedDateTime nowHalifax = openNowCalculator.nowInHalifax();
		DayOfWeek today = nowHalifax.getDayOfWeek();
		DayOfWeek yesterday = today.minus(1);
		LocalTime now = nowHalifax.toLocalTime();

		var results = resourceRepository.findNearby(latitude, longitude, radiusMetres, categoryId, likePattern,
				costTypeFilter == null ? null : costTypeFilter.name(),
				verificationStatusFilter == null ? null : verificationStatusFilter.name(),
				openNowOnly, today.name(), yesterday.name(), now, unsortedPageable(page, size));

		List<UUID> resourceIds = results.getContent().stream().map(NearbyResourceProjection::getId).toList();
		Map<UUID, List<OperatingHoursEntry>> hoursByResourceId = loadHoursByResourceIds(resourceIds);

		List<NearbyResourceDetails> content = results.getContent().stream()
				.map(projection -> {
					List<OperatingHoursEntry> hours = hoursByResourceId.getOrDefault(projection.getId(), List.of());
					OpenNowCalculator.Result result = openNowCalculator.calculate(hours, nowHalifax);
					return NearbyResourceDetails.from(projection, result.status(), result.openNow());
				})
				.toList();

		NearbyResourcePage nearbyPage = new NearbyResourcePage(content, results.getNumber(), results.getSize(),
				results.getTotalElements(), results.getTotalPages());
		return NearbyResourcePageResponse.from(nearbyPage);
	}

	private static void requireLatitude(Double latitude) {
		if (latitude == null) {
			throw new InvalidLatitudeException("latitude is required.");
		}
		if (!CoordinateValidation.isValidLatitude(latitude)) {
			throw new InvalidLatitudeException("latitude must be a finite number between "
					+ CoordinateValidation.MIN_LATITUDE + " and " + CoordinateValidation.MAX_LATITUDE + ".");
		}
	}

	private static void requireLongitude(Double longitude) {
		if (longitude == null) {
			throw new InvalidLongitudeException("longitude is required.");
		}
		if (!CoordinateValidation.isValidLongitude(longitude)) {
			throw new InvalidLongitudeException("longitude must be a finite number between "
					+ CoordinateValidation.MIN_LONGITUDE + " and " + CoordinateValidation.MAX_LONGITUDE + ".");
		}
	}

	/** Missing/blank means the default 5 km radius; anything outside (0, 50] km is rejected. */
	private static double resolveRadiusMetres(Double radiusKm) {
		double km = radiusKm == null ? DEFAULT_RADIUS_KM : radiusKm;
		if (!Double.isFinite(km) || km <= 0 || km > MAX_RADIUS_KM) {
			throw new InvalidRadiusException(
					"radiusKm must be a finite number greater than 0 and at most " + MAX_RADIUS_KM + " (got '" + radiusKm + "').");
		}
		return km * METRES_PER_KILOMETRE;
	}

	/**
	 * Nearby search has exactly one meaningful order (distance ascending,
	 * baked into the native query itself — see {@code ResourceRepository
	 * .findNearby}), so this deliberately returns a {@code Pageable} with no
	 * {@code Sort} rather than reusing {@link #pageable}, which always
	 * attaches one.
	 */
	private static Pageable unsortedPageable(int page, int size) {
		if (page < 0) {
			throw new InvalidPaginationException("page must not be negative.");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidPaginationException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
		}
		return PageRequest.of(page, size);
	}

	private ResourceDetails toResourceDetailsWithHours(CommunityResource resource) {
		List<OperatingHoursEntry> hours = operatingHoursRepository.findByResourceId(resource.getId()).stream()
				.map(OperatingHoursEntry::from)
				.toList();
		return toResourceDetails(resource, hours, openNowCalculator.nowInHalifax());
	}

	private ResourceDetails toResourceDetails(CommunityResource resource, List<OperatingHoursEntry> hours,
			ZonedDateTime nowHalifax) {
		OpenNowCalculator.Result result = openNowCalculator.calculate(hours, nowHalifax);
		return ResourceDetails.from(resource, result.status(), result.openNow(), hours);
	}

	private Map<UUID, List<OperatingHoursEntry>> loadHoursByResourceIds(List<UUID> resourceIds) {
		if (resourceIds.isEmpty()) {
			return Map.of();
		}
		return operatingHoursRepository.findByResourceIdIn(resourceIds).stream()
				.collect(Collectors.groupingBy(ResourceOperatingHours::getResourceId,
						Collectors.mapping(OperatingHoursEntry::from, Collectors.toList())));
	}

	private static CostType resolveCostType(String costType) {
		if (costType == null || costType.isBlank()) {
			return null;
		}
		try {
			return CostType.valueOf(costType.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new InvalidCostTypeException(
					"costType must be one of " + Arrays.toString(CostType.values()) + " (got '" + costType + "').");
		}
	}

	private static VerificationStatus resolveVerificationStatus(String verificationStatus) {
		if (verificationStatus == null || verificationStatus.isBlank()) {
			return null;
		}
		try {
			return VerificationStatus.valueOf(verificationStatus.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new InvalidVerificationStatusException("verificationStatus must be one of "
					+ Arrays.toString(VerificationStatus.values()) + " (got '" + verificationStatus + "').");
		}
	}

	/** Missing/blank/"false" all mean "no filter"; only "true" narrows to currently-open resources. */
	private static boolean resolveOpenNowFilter(String openNow) {
		if (openNow == null || openNow.isBlank()) {
			return false;
		}
		String normalized = openNow.trim();
		if ("true".equalsIgnoreCase(normalized)) {
			return true;
		}
		if ("false".equalsIgnoreCase(normalized)) {
			return false;
		}
		throw new InvalidOpenNowFilterException("openNow must be 'true' or 'false' (got '" + openNow + "').");
	}

	private CommunityResource findRequiredById(UUID id) {
		return resourceRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.byId(id));
	}

	private Category requireActiveCategory(Long categoryId) {
		if (categoryId == null) {
			throw new ValidationException("The submitted resource contains invalid information.",
					Map.of("categoryId", "Category is required."));
		}
		Category category = categoryRepository.findById(categoryId)
				.orElseThrow(() -> CategoryNotFoundException.forId(categoryId));
		if (!category.isActive()) {
			throw InactiveCategoryException.forId(categoryId);
		}
		return category;
	}

	private static Pageable pageable(int page, int size, String sort) {
		if (page < 0) {
			throw new InvalidPaginationException("page must not be negative.");
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new InvalidPaginationException("size must be between 1 and " + MAX_PAGE_SIZE + ".");
		}
		return PageRequest.of(page, size, resolveSort(sort));
	}

	/**
	 * {@code sort=name} (the default) sorts ascending; {@code sort=createdAt}
	 * sorts newest-first, since a chronological listing is far more useful
	 * read newest-to-oldest than the reverse. No separate direction
	 * parameter is exposed — two fixed, documented behaviors are simpler to
	 * use and to test than a direction/field combinatorial surface, and
	 * nothing in the product requirements justifies more yet.
	 */
	private static Sort resolveSort(String sort) {
		if (sort == null || sort.isBlank() || "name".equals(sort)) {
			return Sort.by("name").ascending();
		}
		if ("createdAt".equals(sort)) {
			return Sort.by("createdAt").descending();
		}
		throw new InvalidSortException("sort must be one of " + ALLOWED_SORT_VALUES + " (got '" + sort + "').");
	}

}
