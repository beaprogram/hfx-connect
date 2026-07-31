package com.hfxconnect.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.error.InvalidCostTypeException;
import com.hfxconnect.common.error.InvalidLatitudeException;
import com.hfxconnect.common.error.InvalidLongitudeException;
import com.hfxconnect.common.error.InvalidOpenNowFilterException;
import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.InvalidRadiusException;
import com.hfxconnect.common.error.InvalidSearchQueryException;
import com.hfxconnect.common.error.InvalidSortException;
import com.hfxconnect.common.error.InvalidVerificationStatusException;
import com.hfxconnect.common.error.ValidationException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises {@link ResourceService}'s full business behavior against the
 * real database (real {@link CategoryRepository}, real
 * {@link ResourceRepository}) rather than mocks — this service's most
 * important rules (category existence/active checks, race-safe slug
 * uniqueness) are exactly the kind of behavior mocking would fail to prove.
 * {@code @Transactional} rolls each test back automatically, since every
 * call here runs directly on the test thread (no HTTP layer exists yet).
 */
@Transactional
class ResourceServiceIntegrationTest extends AbstractPostgresIntegrationTest {

	private static final ZoneId HALIFAX_ZONE = ZoneId.of("America/Halifax");

	@Autowired
	private ResourceService resourceService;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Test
	void createsAValidResource() {
		Category category = activeCategory("Food Assistance");

		ResourceDetails details = resourceService.create(validCommand(category.getId(), "Halifax Food Bank"));

		assertThat(details.id()).isNotNull();
		assertThat(details.name()).isEqualTo("Halifax Food Bank");
		assertThat(details.slug()).isEqualTo("halifax-food-bank");
		assertThat(details.categoryId()).isEqualTo(category.getId());
		assertThat(details.active()).isTrue();
		assertThat(details.verificationStatus()).isEqualTo(VerificationStatus.UNVERIFIED);
	}

	@Test
	void normalizesWhitespaceProvinceAndPostalCodeOnCreate() {
		Category category = activeCategory("Normalization Check");
		CreateResourceCommand command = new CreateResourceCommand(category.getId(), "  Spacey   Name  ",
				"Description", "123 Main St", null, "Halifax", "ns", "b3h4r2", null, null, null, null, null, null);

		ResourceDetails details = resourceService.create(command);

		assertThat(details.name()).isEqualTo("Spacey Name");
		assertThat(details.province()).isEqualTo("NS");
		assertThat(details.postalCode()).isEqualTo("B3H 4R2");
	}

	@Test
	void defaultsCostTypeToUnknownWhenNotProvided() {
		Category category = activeCategory("Cost Type Default");

		ResourceDetails details = resourceService.create(validCommand(category.getId(), "Default Cost Resource"));

		assertThat(details.costType()).isEqualTo(CostType.UNKNOWN);
	}

	@Test
	void rejectsANameThatCannotProduceASlug() {
		Category category = activeCategory("Empty Slug Check");
		CreateResourceCommand command = withName(validCommand(category.getId(), "placeholder"), "&&&");
		long countBefore = resourceRepository.count();

		assertThatThrownBy(() -> resourceService.create(command)).isInstanceOf(ValidationException.class);

		assertThat(resourceRepository.count()).isEqualTo(countBefore);
	}

	@Test
	void rejectsADuplicateSlug() {
		Category category = activeCategory("Duplicate Slug Check");
		resourceService.create(validCommand(category.getId(), "Duplicate Name"));

		assertThatThrownBy(() -> resourceService.create(validCommand(category.getId(), "Duplicate Name")))
				.isInstanceOf(ResourceConflictException.class);
	}

	@Test
	void rejectsCreationUnderAMissingCategory() {
		CreateResourceCommand command = validCommand(-1L, "Missing Category Resource");
		long countBefore = resourceRepository.count();

		assertThatThrownBy(() -> resourceService.create(command)).isInstanceOf(CategoryNotFoundException.class);

		assertThat(resourceRepository.count()).isEqualTo(countBefore);
	}

	@Test
	void rejectsCreationUnderANullCategory() {
		CreateResourceCommand command = validCommand(null, "Null Category Resource");

		assertThatThrownBy(() -> resourceService.create(command)).isInstanceOf(ValidationException.class);
	}

	@Test
	void rejectsCreationUnderAnInactiveCategory() {
		Category category = inactiveCategory("Inactive Category Check");
		CreateResourceCommand command = validCommand(category.getId(), "Inactive Category Resource");
		long countBefore = resourceRepository.count();

		assertThatThrownBy(() -> resourceService.create(command)).isInstanceOf(InactiveCategoryException.class);

		assertThat(resourceRepository.count()).isEqualTo(countBefore);
	}

	@Test
	void getActiveBySlugReturnsTheCreatedResource() {
		Category category = activeCategory("Slug Lookup Check");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Slug Lookup Resource"));

		ResourceDetails found = resourceService.getActiveBySlug(created.slug());

		assertThat(found.id()).isEqualTo(created.id());
	}

	@Test
	void getActiveBySlugThrowsNotFoundForAnUnknownSlug() {
		assertThatThrownBy(() -> resourceService.getActiveBySlug("does-not-exist-" + UUID.randomUUID()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void updateChangesAllowedFields() {
		Category category = activeCategory("Update Fields Check");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Original Name"));

		UpdateResourceCommand update = new UpdateResourceCommand(category.getId(), "Updated Name",
				"Updated description.", "456 Other St", null, "Dartmouth", "NS", "B2Y 1A1", null, null, null,
				CostType.FREE, null, null);
		ResourceDetails updated = resourceService.update(created.id(), update);

		assertThat(updated.name()).isEqualTo("Updated Name");
		assertThat(updated.description()).isEqualTo("Updated description.");
		assertThat(updated.city()).isEqualTo("Dartmouth");
		assertThat(updated.costType()).isEqualTo(CostType.FREE);
	}

	@Test
	void updatePreservesTheOriginalSlugEvenWhenTheNameChanges() {
		Category category = activeCategory("Slug Stability Check");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Original Slug Name"));

		UpdateResourceCommand update = withUpdateName(validUpdate(category.getId()), "A Completely Different Name");
		ResourceDetails updated = resourceService.update(created.id(), update);

		assertThat(updated.slug()).isEqualTo(created.slug());
		assertThat(updated.name()).isEqualTo("A Completely Different Name");
	}

	@Test
	void updateCanMoveAResourceToAnotherActiveCategory() {
		Category originalCategory = activeCategory("Move Source Category");
		Category destinationCategory = activeCategory("Move Destination Category");
		ResourceDetails created = resourceService.create(validCommand(originalCategory.getId(), "Movable Resource"));

		ResourceDetails updated = resourceService.update(created.id(), validUpdate(destinationCategory.getId()));

		assertThat(updated.categoryId()).isEqualTo(destinationCategory.getId());
	}

	@Test
	void updateCannotMoveAResourceToAMissingCategory() {
		Category category = activeCategory("Move To Missing Category Source");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Move To Missing Resource"));

		assertThatThrownBy(() -> resourceService.update(created.id(), validUpdate(-1L)))
				.isInstanceOf(CategoryNotFoundException.class);
	}

	@Test
	void updateCannotMoveAResourceToAnInactiveCategory() {
		Category category = activeCategory("Move To Inactive Category Source");
		Category inactiveDestination = inactiveCategory("Move To Inactive Category Destination");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Move To Inactive Resource"));

		assertThatThrownBy(() -> resourceService.update(created.id(), validUpdate(inactiveDestination.getId())))
				.isInstanceOf(InactiveCategoryException.class);
	}

	@Test
	void updateThrowsNotFoundForAMissingResource() {
		Category category = activeCategory("Update Missing Resource Check");
		UUID missingId = UUID.randomUUID();

		assertThatThrownBy(() -> resourceService.update(missingId, validUpdate(category.getId())))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void deactivateHidesAResourceFromActiveLookups() {
		Category category = activeCategory("Deactivate Visibility Check");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Deactivate Me"));

		resourceService.deactivate(created.id());

		assertThatThrownBy(() -> resourceService.getActiveBySlug(created.slug()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void deactivateThrowsNotFoundForAMissingResource() {
		UUID missingId = UUID.randomUUID();

		assertThatThrownBy(() -> resourceService.deactivate(missingId)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void rejectsADangerousWebsiteSchemeEndToEnd() {
		Category category = activeCategory("Dangerous Website Check");
		CreateResourceCommand command = withWebsite(
				validCommand(category.getId(), "Dangerous Website Resource"), "javascript:alert(1)");
		long countBefore = resourceRepository.count();

		assertThatThrownBy(() -> resourceService.create(command)).isInstanceOf(ValidationException.class);
		assertThat(resourceRepository.count()).isEqualTo(countBefore);
	}

	@Test
	void listActiveExcludesDeactivatedResources() {
		Category category = activeCategory("List Active Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "List Active " + marker));
		resourceService.deactivate(created.id());

		ResourcePage page = resourceService.search(null, null, null, null, null, 0, 100, null);

		assertThat(page.content()).extracting(ResourceDetails::name).doesNotContain("List Active " + marker);
	}

	@Test
	void listActiveByCategoryOnlyReturnsResourcesInThatCategory() {
		Category categoryA = activeCategory("List By Category A");
		Category categoryB = activeCategory("List By Category B");
		String marker = UUID.randomUUID().toString();
		resourceService.create(validCommand(categoryA.getId(), "In Category A " + marker));
		resourceService.create(validCommand(categoryB.getId(), "In Category B " + marker));

		ResourcePage page = resourceService.search(null, categoryA.getId(), null, null, null, 0, 100, null);

		assertThat(page.content()).extracting(ResourceDetails::name)
				.contains("In Category A " + marker)
				.doesNotContain("In Category B " + marker);
	}

	@Test
	void listActiveRejectsAnOutOfRangePageSize() {
		assertThatThrownBy(() -> resourceService.search(null, null, null, null, null, 0, 0, null)).isInstanceOf(InvalidPaginationException.class);
		assertThatThrownBy(() -> resourceService.search(null, null, null, null, null, 0, 1000, null)).isInstanceOf(InvalidPaginationException.class);
	}

	@Test
	void listActiveRejectsANegativePage() {
		assertThatThrownBy(() -> resourceService.search(null, null, null, null, null, -1, 20, null)).isInstanceOf(InvalidPaginationException.class);
	}

	@Test
	void listActiveDefaultsToNameAscending() {
		Category category = activeCategory("Default Sort Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(validCommand(category.getId(), "B Resource " + marker));
		resourceService.create(validCommand(category.getId(), "A Resource " + marker));

		ResourcePage page = resourceService.search(null, category.getId(), null, null, null, 0, 100, null);

		assertThat(page.content()).extracting(ResourceDetails::name)
				.containsExactly("A Resource " + marker, "B Resource " + marker);
	}

	@Test
	void listActiveSortsByCreatedAtDescendingWhenRequested() {
		Category category = activeCategory("CreatedAt Sort Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails first = resourceService.create(validCommand(category.getId(), "First " + marker));
		ResourceDetails second = resourceService.create(validCommand(category.getId(), "Second " + marker));

		ResourcePage page = resourceService.search(null, category.getId(), null, null, null, 0, 100, "createdAt");

		assertThat(page.content()).extracting(ResourceDetails::id)
				.containsSubsequence(second.id(), first.id());
	}

	@Test
	void listActiveRejectsAnUnsupportedSortValue() {
		assertThatThrownBy(() -> resourceService.search(null, null, null, null, null, 0, 20, "notARealField"))
				.isInstanceOf(InvalidSortException.class);
	}

	// ---- Keyword search (Milestone 6A) — see ADR-010 ----

	@Test
	void searchMatchesResourceName() {
		Category category = activeCategory("Search Name Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Halifax Food Bank " + marker));

		ResourcePage page = resourceService.search("Food Bank " + marker, null, null, null, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Halifax Food Bank " + marker);
	}

	@Test
	void searchMatchesResourceDescription() {
		Category category = activeCategory("Search Description Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withDescription(withName(validCommand(category.getId(), "Distinct Name " + marker), "Distinct Name " + marker),
				"Offers free tutoring " + marker + " for newcomers."));

		ResourcePage page = resourceService.search("tutoring " + marker, null, null, null, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Distinct Name " + marker);
	}

	@Test
	void searchMatchesAddressLine1() {
		Category category = activeCategory("Search Address Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withAddressLine1(withName(validCommand(category.getId(), "placeholder"), "Address Match " + marker),
				"742 Evergreen Terrace " + marker));

		ResourcePage page = resourceService.search("Evergreen Terrace " + marker, null, null, null, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Address Match " + marker);
	}

	@Test
	void searchMatchesCity() {
		Category category = activeCategory("Search City Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withCity(withName(validCommand(category.getId(), "placeholder"), "City Match " + marker),
				"Dartmouth" + marker));

		ResourcePage page = resourceService.search("Dartmouth" + marker, null, null, null, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("City Match " + marker);
	}

	@Test
	void searchIsCaseInsensitive() {
		Category category = activeCategory("Search Case Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Library Services " + marker));

		ResourcePage page = resourceService.search("LIBRARY services " + marker.toUpperCase(Locale.ROOT), null, null, null, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Library Services " + marker);
	}

	@Test
	void searchReturnsAnEmptyPageWhenNothingMatches() {
		activeCategory("Search No Match Check");

		ResourcePage page = resourceService.search("no-resource-should-ever-match-this-" + UUID.randomUUID(), null, null, null, null, 0, 20, null);

		assertThat(page.content()).isEmpty();
		assertThat(page.totalElements()).isZero();
	}

	@Test
	void searchExcludesDeactivatedResources() {
		Category category = activeCategory("Search Deactivated Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails created = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Deactivated Search " + marker));
		resourceService.deactivate(created.id());

		ResourcePage page = resourceService.search("Deactivated Search " + marker, null, null, null, null, 0, 20, null);

		assertThat(page.content()).isEmpty();
	}

	@Test
	void searchCombinedWithCategoryOnlyReturnsMatchesInThatCategory() {
		Category categoryA = activeCategory("Search Combo A");
		Category categoryB = activeCategory("Search Combo B");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withName(validCommand(categoryA.getId(), "placeholder"), "Workshop A " + marker));
		resourceService.create(withName(validCommand(categoryB.getId(), "placeholder"), "Workshop B " + marker));

		// The keyword alone ("marker") matches both resources; the category
		// filter is what must narrow it down to just the one in categoryA.
		ResourcePage page = resourceService.search(marker, categoryA.getId(), null, null, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Workshop A " + marker);
	}

	@Test
	void searchCombinedWithCategoryReturnsEmptyWhenTheCategoryHasNoMatch() {
		Category categoryA = activeCategory("Search Combo Empty A");
		Category categoryB = activeCategory("Search Combo Empty B");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withName(validCommand(categoryA.getId(), "placeholder"), "Only In A " + marker));

		ResourcePage page = resourceService.search("Only In A " + marker, categoryB.getId(), null, null, null, 0, 20, null);

		assertThat(page.content()).isEmpty();
	}

	@Test
	void searchPaginatesResults() {
		Category category = activeCategory("Search Pagination Check");
		String marker = UUID.randomUUID().toString();
		for (int i = 0; i < 3; i++) {
			resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Paginated " + marker + " " + i));
		}

		ResourcePage firstPage = resourceService.search("Paginated " + marker, null, null, null, null, 0, 2, null);
		ResourcePage secondPage = resourceService.search("Paginated " + marker, null, null, null, null, 1, 2, null);

		assertThat(firstPage.content()).hasSize(2);
		assertThat(secondPage.content()).hasSize(1);
		assertThat(firstPage.totalElements()).isEqualTo(3);
	}

	@Test
	void searchPreservesNameAscendingSortByDefault() {
		Category category = activeCategory("Search Sort Name Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withName(validCommand(category.getId(), "placeholder"), "B Sorted " + marker));
		resourceService.create(withName(validCommand(category.getId(), "placeholder"), "A Sorted " + marker));

		ResourcePage page = resourceService.search("Sorted " + marker, null, null, null, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name)
				.containsExactly("A Sorted " + marker, "B Sorted " + marker);
	}

	@Test
	void searchPreservesCreatedAtDescendingSortWhenRequested() {
		Category category = activeCategory("Search Sort CreatedAt Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails first = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "First Sorted " + marker));
		ResourceDetails second = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Second Sorted " + marker));

		ResourcePage page = resourceService.search("Sorted " + marker, null, null, null, null, 0, 20, "createdAt");

		assertThat(page.content()).extracting(ResourceDetails::id).containsExactly(second.id(), first.id());
	}

	@Test
	void searchTreatsAPercentSignAsALiteralCharacterNotAWildcard() {
		Category category = activeCategory("Search Percent Literal Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withDescription(withName(validCommand(category.getId(), "placeholder"), "Percent Literal " + marker),
				"Save 50% " + marker + " on services."));
		// A resource that would match "50" alone (via the % wildcard misfiring)
		// but must NOT match the literal phrase "50% ..." — proves % isn't
		// silently acting as an unintended wildcard.
		resourceService.create(withDescription(withName(validCommand(category.getId(), "placeholder"), "Fifty Only " + marker),
				"Serves 50 clients " + marker + " per day."));

		ResourcePage page = resourceService.search("50% " + marker, null, null, null, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Percent Literal " + marker);
	}

	@Test
	void searchTreatsAnUnderscoreAsALiteralCharacterNotAWildcard() {
		Category category = activeCategory("Search Underscore Literal Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withDescription(withName(validCommand(category.getId(), "placeholder"), "Underscore Literal " + marker),
				"Contact user_name " + marker + " for details."));
		resourceService.create(withDescription(withName(validCommand(category.getId(), "placeholder"), "No Underscore " + marker),
				"Contact userXname " + marker + " for details."));

		ResourcePage page = resourceService.search("user_name " + marker, null, null, null, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Underscore Literal " + marker);
	}

	@Test
	void blankSearchQueryBehavesAsNoKeywordFilter() {
		Category category = activeCategory("Search Blank Query Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Blank Query " + marker));

		ResourcePage page = resourceService.search("   ", category.getId(), null, null, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Blank Query " + marker);
	}

	@Test
	void searchRejectsAQueryOverTheMaximumLength() {
		String tooLong = "a".repeat(ResourceSearchQuery.MAX_LENGTH + 1);
		assertThatThrownBy(() -> resourceService.search(tooLong, null, null, null, null, 0, 20, null))
				.isInstanceOf(InvalidSearchQueryException.class);
	}

	@Test
	void getActiveByIdReturnsTheCreatedResource() {
		Category category = activeCategory("Get By Id Check");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Get By Id Resource"));

		ResourceDetails found = resourceService.getActiveById(created.id());

		assertThat(found.id()).isEqualTo(created.id());
	}

	@Test
	void getActiveByIdThrowsNotFoundForADeactivatedResource() {
		Category category = activeCategory("Get By Id Inactive Check");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Get By Id Inactive Resource"));
		resourceService.deactivate(created.id());

		assertThatThrownBy(() -> resourceService.getActiveById(created.id()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void getActiveByIdThrowsNotFoundForAnUnknownId() {
		assertThatThrownBy(() -> resourceService.getActiveById(UUID.randomUUID()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	// ---- Cost/verification/openNow filters and operating hours (Milestone 6B) — see ADR-011 ----

	@Test
	void aResourceWithNoScheduleHasUnknownHoursStatus() {
		Category category = activeCategory("Unknown Hours Check");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "No Schedule Resource"));

		ResourceDetails found = resourceService.getActiveById(created.id());

		assertThat(found.hoursStatus()).isEqualTo(HoursStatus.UNKNOWN);
		assertThat(found.openNow()).isNull();
		assertThat(found.weeklyHours()).isEmpty();
	}

	@Test
	void costTypeFilterOnlyReturnsMatchingResources() {
		Category category = activeCategory("Cost Filter Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withCostType(withName(validCommand(category.getId(), "placeholder"), "Free Resource " + marker), CostType.FREE));
		resourceService.create(withCostType(withName(validCommand(category.getId(), "placeholder"), "Paid Resource " + marker), CostType.PAID));

		ResourcePage page = resourceService.search(marker, null, "FREE", null, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Free Resource " + marker);
	}

	@Test
	void costTypeFilterIsCaseInsensitive() {
		Category category = activeCategory("Cost Filter Case Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withCostType(withName(validCommand(category.getId(), "placeholder"), "Lower Free " + marker), CostType.FREE));

		ResourcePage page = resourceService.search(marker, null, "free", null, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Lower Free " + marker);
	}

	@Test
	void invalidCostTypeFilterThrows() {
		assertThatThrownBy(() -> resourceService.search(null, null, "NOT_A_REAL_COST_TYPE", null, null, 0, 20, null))
				.isInstanceOf(InvalidCostTypeException.class);
	}

	@Test
	void verificationStatusFilterOnlyReturnsMatchingResources() {
		Category category = activeCategory("Verification Filter Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Unverified Resource " + marker));

		ResourcePage page = resourceService.search(marker, null, null, "UNVERIFIED", null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Unverified Resource " + marker);

		ResourcePage verifiedOnly = resourceService.search(marker, null, null, "VERIFIED", null, 0, 20, null);
		assertThat(verifiedOnly.content()).isEmpty();
	}

	@Test
	void invalidVerificationStatusFilterThrows() {
		assertThatThrownBy(() -> resourceService.search(null, null, null, "NOT_A_REAL_STATUS", null, 0, 20, null))
				.isInstanceOf(InvalidVerificationStatusException.class);
	}

	@Test
	void invalidOpenNowFilterThrows() {
		assertThatThrownBy(() -> resourceService.search(null, null, null, null, "maybe", 0, 20, null))
				.isInstanceOf(InvalidOpenNowFilterException.class);
	}

	@Test
	void openNowFilterExcludesResourcesWithNoSchedule() {
		Category category = activeCategory("Open Now Unknown Excluded Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withName(validCommand(category.getId(), "placeholder"), "No Hours At All " + marker));

		ResourcePage page = resourceService.search(marker, null, null, null, "true", 0, 20, null);

		assertThat(page.content()).isEmpty();
	}

	@Test
	void openNowFilterReturnsOnlyCurrentlyOpenResources() {
		Category category = activeCategory("Open Now Filter Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails openResource = resourceService.create(
				withName(validCommand(category.getId(), "placeholder"), "Currently Open " + marker));
		ResourceDetails closedResource = resourceService.create(
				withName(validCommand(category.getId(), "placeholder"), "Currently Closed " + marker));

		DayOfWeek today = ZonedDateTime.now(HALIFAX_ZONE).getDayOfWeek();
		LocalTime now = ZonedDateTime.now(HALIFAX_ZONE).toLocalTime();
		resourceService.replaceOperatingHours(openResource.id(), new ReplaceOperatingHoursRequest(
				List.of(new OperatingHoursEntryRequest(today, false, now.minusHours(1), now.plusHours(1)))));
		resourceService.replaceOperatingHours(closedResource.id(), new ReplaceOperatingHoursRequest(
				List.of(new OperatingHoursEntryRequest(today, true, null, null))));

		ResourcePage page = resourceService.search(marker, null, null, null, "true", 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Currently Open " + marker);
	}

	@Test
	void openNowFilterCombinesWithKeywordAndCategory() {
		Category category = activeCategory("Open Now Combo Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails openResource = resourceService.create(
				withName(validCommand(category.getId(), "placeholder"), "Combo Open " + marker));

		DayOfWeek today = ZonedDateTime.now(HALIFAX_ZONE).getDayOfWeek();
		LocalTime now = ZonedDateTime.now(HALIFAX_ZONE).toLocalTime();
		resourceService.replaceOperatingHours(openResource.id(), new ReplaceOperatingHoursRequest(
				List.of(new OperatingHoursEntryRequest(today, false, now.minusHours(1), now.plusHours(1)))));

		ResourcePage page = resourceService.search(marker, category.getId(), null, null, "true", 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Combo Open " + marker);
	}

	@Test
	void missingOrFalseOpenNowAppliesNoFilter() {
		Category category = activeCategory("Open Now Absent Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withName(validCommand(category.getId(), "placeholder"), "No Filter Applied " + marker));

		ResourcePage missing = resourceService.search(marker, null, null, null, null, 0, 20, null);
		ResourcePage blank = resourceService.search(marker, null, null, null, "", 0, 20, null);
		ResourcePage explicitFalse = resourceService.search(marker, null, null, null, "false", 0, 20, null);

		assertThat(missing.content()).extracting(ResourceDetails::name).containsExactly("No Filter Applied " + marker);
		assertThat(blank.content()).extracting(ResourceDetails::name).containsExactly("No Filter Applied " + marker);
		assertThat(explicitFalse.content()).extracting(ResourceDetails::name).containsExactly("No Filter Applied " + marker);
	}

	@Test
	void allFiltersCombinedNarrowToTheExactMatch() {
		Category category = activeCategory("All Filters Combined Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails match = resourceService.create(withCostType(
				withName(validCommand(category.getId(), "placeholder"), "Matches Every Filter " + marker), CostType.FREE));
		resourceService.create(withCostType(
				withName(validCommand(category.getId(), "placeholder"), "Wrong Cost Type " + marker), CostType.PAID));

		DayOfWeek today = ZonedDateTime.now(HALIFAX_ZONE).getDayOfWeek();
		LocalTime now = ZonedDateTime.now(HALIFAX_ZONE).toLocalTime();
		resourceService.replaceOperatingHours(match.id(), new ReplaceOperatingHoursRequest(
				List.of(new OperatingHoursEntryRequest(today, false, now.minusHours(1), now.plusHours(1)))));

		ResourcePage page = resourceService.search(
				marker, category.getId(), "FREE", "UNVERIFIED", "true", 0, 20, "name");

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Matches Every Filter " + marker);
	}

	@Test
	void replaceOperatingHoursReplacesTheFullWeek() {
		Category category = activeCategory("Replace Hours Check");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Replace Hours Resource"));

		OperatingHoursResponse response = resourceService.replaceOperatingHours(created.id(), new ReplaceOperatingHoursRequest(List.of(
				new OperatingHoursEntryRequest(DayOfWeek.MONDAY, false, LocalTime.of(9, 0), LocalTime.of(17, 0)),
				new OperatingHoursEntryRequest(DayOfWeek.TUESDAY, true, null, null))));

		assertThat(response.weeklyHours()).hasSize(2);
		assertThat(response.timezone()).isEqualTo("America/Halifax");
		assertThat(response.weeklyHours().get(0).dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(response.weeklyHours().get(1).dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
	}

	@Test
	void replaceOperatingHoursFullyReplacesPreviouslySetDays() {
		Category category = activeCategory("Replace Hours Overwrite Check");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Overwrite Hours Resource"));
		resourceService.replaceOperatingHours(created.id(), new ReplaceOperatingHoursRequest(List.of(
				new OperatingHoursEntryRequest(DayOfWeek.MONDAY, false, LocalTime.of(9, 0), LocalTime.of(17, 0)),
				new OperatingHoursEntryRequest(DayOfWeek.TUESDAY, false, LocalTime.of(9, 0), LocalTime.of(17, 0)))));

		OperatingHoursResponse response = resourceService.replaceOperatingHours(created.id(),
				new ReplaceOperatingHoursRequest(List.of(
						new OperatingHoursEntryRequest(DayOfWeek.WEDNESDAY, false, LocalTime.of(10, 0), LocalTime.of(14, 0)))));

		assertThat(response.weeklyHours()).extracting(OperatingHoursEntryResponse::dayOfWeek)
				.containsExactly(DayOfWeek.WEDNESDAY);
	}

	@Test
	void replaceOperatingHoursThrowsNotFoundForAMissingResource() {
		assertThatThrownBy(() -> resourceService.replaceOperatingHours(UUID.randomUUID(),
				new ReplaceOperatingHoursRequest(List.of(
						new OperatingHoursEntryRequest(DayOfWeek.MONDAY, false, LocalTime.of(9, 0), LocalTime.of(17, 0))))))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void replaceOperatingHoursSucceedsForADeactivatedResource() {
		Category category = activeCategory("Replace Hours Inactive Check");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Inactive Hours Resource"));
		resourceService.deactivate(created.id());

		OperatingHoursResponse response = resourceService.replaceOperatingHours(created.id(),
				new ReplaceOperatingHoursRequest(List.of(
						new OperatingHoursEntryRequest(DayOfWeek.MONDAY, false, LocalTime.of(9, 0), LocalTime.of(17, 0)))));

		assertThat(response.weeklyHours()).hasSize(1);
	}

	@Test
	void replaceOperatingHoursRejectsAnInvalidSchedule() {
		Category category = activeCategory("Replace Hours Invalid Check");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Invalid Hours Resource"));

		assertThatThrownBy(() -> resourceService.replaceOperatingHours(created.id(), new ReplaceOperatingHoursRequest(List.of(
				new OperatingHoursEntryRequest(DayOfWeek.MONDAY, false, LocalTime.of(9, 0), LocalTime.of(9, 0))))))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void weeklyHoursAppearOnBatchLoadedListResultsWithoutNPlusOne() {
		Category category = activeCategory("Batch Load Hours Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails created = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Batch Hours " + marker));
		resourceService.replaceOperatingHours(created.id(), new ReplaceOperatingHoursRequest(List.of(
				new OperatingHoursEntryRequest(DayOfWeek.MONDAY, false, LocalTime.of(9, 0), LocalTime.of(17, 0)))));

		ResourcePage page = resourceService.search(marker, null, null, null, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Batch Hours " + marker);
		assertThat(page.content().get(0).hoursStatus()).isNotEqualTo(HoursStatus.UNKNOWN);
		assertThat(page.content().get(0).weeklyHours()).hasSize(1);
	}

	// ---- Resource location and nearby search (Milestone 7A) — see ADR-012 ----

	// Halifax Central Library, Dalhousie University, and Toronto — real,
	// asymmetric coordinates chosen so a latitude/longitude swap or a
	// distance-ordering bug would be immediately, obviously wrong.
	private static final double LIBRARY_LAT = 44.6488;
	private static final double LIBRARY_LON = -63.5752;
	private static final double DALHOUSIE_LAT = 44.6366;
	private static final double DALHOUSIE_LON = -63.5934;
	private static final double TORONTO_LAT = 43.6532;
	private static final double TORONTO_LON = -79.3832;

	@Test
	void replaceLocationSetsTheResourcesCoordinates() {
		Category category = activeCategory("Replace Location Check");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Replace Location Resource"));

		ResourceLocationResponse response = resourceService.replaceLocation(
				created.id(), new ResourceLocationRequest(LIBRARY_LAT, LIBRARY_LON));

		assertThat(response.resourceId()).isEqualTo(created.id());
		assertThat(response.latitude()).isEqualTo(LIBRARY_LAT);
		assertThat(response.longitude()).isEqualTo(LIBRARY_LON);
	}

	@Test
	void replaceLocationCanCorrectAnExistingCoordinate() {
		Category category = activeCategory("Correct Location Check");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Correct Location Resource"));
		resourceService.replaceLocation(created.id(), new ResourceLocationRequest(TORONTO_LAT, TORONTO_LON));

		ResourceLocationResponse response = resourceService.replaceLocation(
				created.id(), new ResourceLocationRequest(LIBRARY_LAT, LIBRARY_LON));

		assertThat(response.latitude()).isEqualTo(LIBRARY_LAT);
		assertThat(response.longitude()).isEqualTo(LIBRARY_LON);
	}

	@Test
	void replaceLocationThrowsNotFoundForAMissingResource() {
		assertThatThrownBy(() -> resourceService.replaceLocation(
				UUID.randomUUID(), new ResourceLocationRequest(LIBRARY_LAT, LIBRARY_LON)))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void replaceLocationRejectsOutOfRangeCoordinates() {
		Category category = activeCategory("Invalid Location Check");
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "Invalid Location Resource"));

		assertThatThrownBy(() -> resourceService.replaceLocation(created.id(), new ResourceLocationRequest(91.0, 0.0)))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void nearbyReturnsAResourceInsideTheRadiusOrderedNearestFirst() {
		Category category = activeCategory("Nearby Distance Order Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails near = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Nearby Near " + marker));
		ResourceDetails far = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Nearby Far " + marker));
		resourceService.replaceLocation(near.id(), new ResourceLocationRequest(DALHOUSIE_LAT, DALHOUSIE_LON));
		resourceService.replaceLocation(far.id(), new ResourceLocationRequest(LIBRARY_LAT + 0.02, LIBRARY_LON + 0.02));

		NearbyResourcePageResponse response = resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, 10.0, marker, null, null, null, null, 0, 20);

		assertThat(response.content()).extracting(NearbyResourceSummaryResponse::name)
				.containsExactly("Nearby Near " + marker, "Nearby Far " + marker);
		assertThat(response.content().get(0).distanceMeters()).isLessThan(response.content().get(1).distanceMeters());
	}

	@Test
	void nearbyExcludesResourcesOutsideTheRadius() {
		Category category = activeCategory("Nearby Radius Exclusion Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails outside = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Nearby Outside " + marker));
		resourceService.replaceLocation(outside.id(), new ResourceLocationRequest(TORONTO_LAT, TORONTO_LON));

		NearbyResourcePageResponse response = resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, 10.0, marker, null, null, null, null, 0, 20);

		assertThat(response.content()).isEmpty();
	}

	@Test
	void nearbyExcludesResourcesWithNoLocation() {
		Category category = activeCategory("Nearby No Location Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Nearby No Location " + marker));

		NearbyResourcePageResponse response = resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, ResourceService.MAX_RADIUS_KM, marker, null, null, null, null, 0, 20);

		assertThat(response.content()).isEmpty();
	}

	@Test
	void nearbyExcludesInactiveResources() {
		Category category = activeCategory("Nearby Inactive Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails created = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Nearby Inactive " + marker));
		resourceService.replaceLocation(created.id(), new ResourceLocationRequest(LIBRARY_LAT, LIBRARY_LON));
		resourceService.deactivate(created.id());

		NearbyResourcePageResponse response = resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, 1.0, marker, null, null, null, null, 0, 20);

		assertThat(response.content()).isEmpty();
	}

	@Test
	void nearbyUsesTheDefaultRadiusWhenNotSpecified() {
		Category category = activeCategory("Nearby Default Radius Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails created = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Nearby Default Radius " + marker));
		resourceService.replaceLocation(created.id(), new ResourceLocationRequest(DALHOUSIE_LAT, DALHOUSIE_LON));

		NearbyResourcePageResponse response = resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, null, marker, null, null, null, null, 0, 20);

		assertThat(response.content()).extracting(NearbyResourceSummaryResponse::name)
				.containsExactly("Nearby Default Radius " + marker);
	}

	@Test
	void nearbyReturnsAnEmptyPageWhenNothingMatches() {
		NearbyResourcePageResponse response = resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, 1.0, "no-resource-should-ever-match-" + UUID.randomUUID(),
				null, null, null, null, 0, 20);

		assertThat(response.content()).isEmpty();
		assertThat(response.totalElements()).isZero();
	}

	@Test
	void nearbyRejectsAMissingLatitude() {
		assertThatThrownBy(() -> resourceService.nearby(null, LIBRARY_LON, null, null, null, null, null, null, 0, 20))
				.isInstanceOf(InvalidLatitudeException.class);
	}

	@Test
	void nearbyRejectsAMissingLongitude() {
		assertThatThrownBy(() -> resourceService.nearby(LIBRARY_LAT, null, null, null, null, null, null, null, 0, 20))
				.isInstanceOf(InvalidLongitudeException.class);
	}

	@Test
	void nearbyRejectsAnOutOfRangeLatitude() {
		assertThatThrownBy(() -> resourceService.nearby(91.0, LIBRARY_LON, null, null, null, null, null, null, 0, 20))
				.isInstanceOf(InvalidLatitudeException.class);
	}

	@Test
	void nearbyRejectsAnOutOfRangeLongitude() {
		assertThatThrownBy(() -> resourceService.nearby(LIBRARY_LAT, 181.0, null, null, null, null, null, null, 0, 20))
				.isInstanceOf(InvalidLongitudeException.class);
	}

	@Test
	void nearbyRejectsANonPositiveRadius() {
		assertThatThrownBy(() -> resourceService.nearby(LIBRARY_LAT, LIBRARY_LON, 0.0, null, null, null, null, null, 0, 20))
				.isInstanceOf(InvalidRadiusException.class);
		assertThatThrownBy(() -> resourceService.nearby(LIBRARY_LAT, LIBRARY_LON, -5.0, null, null, null, null, null, 0, 20))
				.isInstanceOf(InvalidRadiusException.class);
	}

	@Test
	void nearbyRejectsARadiusAboveTheMaximum() {
		assertThatThrownBy(() -> resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, ResourceService.MAX_RADIUS_KM + 1, null, null, null, null, null, 0, 20))
				.isInstanceOf(InvalidRadiusException.class);
	}

	@Test
	void nearbyCombinesWithCategoryFilter() {
		Category categoryA = activeCategory("Nearby Category Combo A");
		Category categoryB = activeCategory("Nearby Category Combo B");
		String marker = UUID.randomUUID().toString();
		ResourceDetails inA = resourceService.create(withName(validCommand(categoryA.getId(), "placeholder"), "Nearby In A " + marker));
		ResourceDetails inB = resourceService.create(withName(validCommand(categoryB.getId(), "placeholder"), "Nearby In B " + marker));
		resourceService.replaceLocation(inA.id(), new ResourceLocationRequest(LIBRARY_LAT, LIBRARY_LON));
		resourceService.replaceLocation(inB.id(), new ResourceLocationRequest(LIBRARY_LAT, LIBRARY_LON));

		NearbyResourcePageResponse response = resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, 5.0, marker, categoryA.getId(), null, null, null, 0, 20);

		assertThat(response.content()).extracting(NearbyResourceSummaryResponse::name)
				.containsExactly("Nearby In A " + marker);
	}

	@Test
	void nearbyCombinesWithCostTypeFilter() {
		Category category = activeCategory("Nearby Cost Combo");
		String marker = UUID.randomUUID().toString();
		ResourceDetails free = resourceService.create(withCostType(withName(validCommand(category.getId(), "placeholder"), "Nearby Free " + marker), CostType.FREE));
		ResourceDetails paid = resourceService.create(withCostType(withName(validCommand(category.getId(), "placeholder"), "Nearby Paid " + marker), CostType.PAID));
		resourceService.replaceLocation(free.id(), new ResourceLocationRequest(LIBRARY_LAT, LIBRARY_LON));
		resourceService.replaceLocation(paid.id(), new ResourceLocationRequest(LIBRARY_LAT, LIBRARY_LON));

		NearbyResourcePageResponse response = resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, 5.0, marker, null, "FREE", null, null, 0, 20);

		assertThat(response.content()).extracting(NearbyResourceSummaryResponse::name)
				.containsExactly("Nearby Free " + marker);
	}

	@Test
	void nearbyCombinesWithVerificationStatusFilter() {
		Category category = activeCategory("Nearby Verification Combo");
		String marker = UUID.randomUUID().toString();
		ResourceDetails created = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Nearby Unverified " + marker));
		resourceService.replaceLocation(created.id(), new ResourceLocationRequest(LIBRARY_LAT, LIBRARY_LON));

		NearbyResourcePageResponse matching = resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, 5.0, marker, null, null, "UNVERIFIED", null, 0, 20);
		NearbyResourcePageResponse nonMatching = resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, 5.0, marker, null, null, "VERIFIED", null, 0, 20);

		assertThat(matching.content()).extracting(NearbyResourceSummaryResponse::name)
				.containsExactly("Nearby Unverified " + marker);
		assertThat(nonMatching.content()).isEmpty();
	}

	@Test
	void nearbyCombinesWithOpenNowFilter() {
		Category category = activeCategory("Nearby Open Now Combo");
		String marker = UUID.randomUUID().toString();
		ResourceDetails open = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Nearby Open " + marker));
		ResourceDetails unknown = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Nearby Unknown Hours " + marker));
		resourceService.replaceLocation(open.id(), new ResourceLocationRequest(LIBRARY_LAT, LIBRARY_LON));
		resourceService.replaceLocation(unknown.id(), new ResourceLocationRequest(LIBRARY_LAT, LIBRARY_LON));

		DayOfWeek today = ZonedDateTime.now(HALIFAX_ZONE).getDayOfWeek();
		LocalTime now = ZonedDateTime.now(HALIFAX_ZONE).toLocalTime();
		resourceService.replaceOperatingHours(open.id(), new ReplaceOperatingHoursRequest(
				List.of(new OperatingHoursEntryRequest(today, false, now.minusHours(1), now.plusHours(1)))));

		NearbyResourcePageResponse response = resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, 5.0, marker, null, null, null, "true", 0, 20);

		assertThat(response.content()).extracting(NearbyResourceSummaryResponse::name)
				.containsExactly("Nearby Open " + marker);
	}

	@Test
	void nearbyPaginatesResults() {
		Category category = activeCategory("Nearby Pagination Check");
		String marker = UUID.randomUUID().toString();
		for (int i = 0; i < 3; i++) {
			ResourceDetails created = resourceService.create(
					withName(validCommand(category.getId(), "placeholder"), "Nearby Paginated " + marker + " " + i));
			resourceService.replaceLocation(created.id(), new ResourceLocationRequest(LIBRARY_LAT, LIBRARY_LON));
		}

		NearbyResourcePageResponse firstPage = resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, 5.0, marker, null, null, null, null, 0, 2);
		NearbyResourcePageResponse secondPage = resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, 5.0, marker, null, null, null, null, 1, 2);

		assertThat(firstPage.content()).hasSize(2);
		assertThat(secondPage.content()).hasSize(1);
		assertThat(firstPage.totalElements()).isEqualTo(3);
	}

	@Test
	void nearbyResultsIncludeDistanceCoordinatesAndHoursStatus() {
		Category category = activeCategory("Nearby Result Shape Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails created = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Nearby Result Shape " + marker));
		resourceService.replaceLocation(created.id(), new ResourceLocationRequest(LIBRARY_LAT, LIBRARY_LON));

		NearbyResourcePageResponse response = resourceService.nearby(
				LIBRARY_LAT, LIBRARY_LON, 1.0, marker, null, null, null, null, 0, 20);

		NearbyResourceSummaryResponse result = response.content().get(0);
		assertThat(result.latitude()).isEqualTo(LIBRARY_LAT);
		assertThat(result.longitude()).isEqualTo(LIBRARY_LON);
		assertThat(result.distanceMeters()).isEqualTo(0.0, org.assertj.core.data.Offset.offset(1.0));
		assertThat(result.hoursStatus()).isEqualTo(HoursStatus.UNKNOWN);
	}

	private Category activeCategory(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " " + marker;
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "cat-" + marker, null));
	}

	private Category inactiveCategory(String namePrefix) {
		Category category = activeCategory(namePrefix);
		category.deactivate();
		return categoryRepository.saveAndFlush(category);
	}

	private static CreateResourceCommand validCommand(Long categoryId, String name) {
		return new CreateResourceCommand(categoryId, name, "A helpful community resource.", "123 Main St", null,
				"Halifax", "NS", "B3H 4R2", null, null, null, null, null, null);
	}

	private static UpdateResourceCommand validUpdate(Long categoryId) {
		return new UpdateResourceCommand(categoryId, "Updated Resource Name", "An updated description.",
				"789 Update Ave", null, "Halifax", "NS", "B3H 4R2", null, null, null, null, null, null);
	}

	private static CreateResourceCommand withName(CreateResourceCommand base, String name) {
		return new CreateResourceCommand(base.categoryId(), name, base.description(), base.addressLine1(),
				base.addressLine2(), base.city(), base.province(), base.postalCode(), base.phone(), base.email(),
				base.websiteUrl(), base.costType(), base.costDetails(), base.eligibility());
	}

	private static CreateResourceCommand withCostType(CreateResourceCommand base, CostType costType) {
		return new CreateResourceCommand(base.categoryId(), base.name(), base.description(), base.addressLine1(),
				base.addressLine2(), base.city(), base.province(), base.postalCode(), base.phone(), base.email(),
				base.websiteUrl(), costType, base.costDetails(), base.eligibility());
	}

	private static CreateResourceCommand withWebsite(CreateResourceCommand base, String websiteUrl) {
		return new CreateResourceCommand(base.categoryId(), base.name(), base.description(), base.addressLine1(),
				base.addressLine2(), base.city(), base.province(), base.postalCode(), base.phone(), base.email(),
				websiteUrl, base.costType(), base.costDetails(), base.eligibility());
	}

	private static CreateResourceCommand withDescription(CreateResourceCommand base, String description) {
		return new CreateResourceCommand(base.categoryId(), base.name(), description, base.addressLine1(),
				base.addressLine2(), base.city(), base.province(), base.postalCode(), base.phone(), base.email(),
				base.websiteUrl(), base.costType(), base.costDetails(), base.eligibility());
	}

	private static CreateResourceCommand withAddressLine1(CreateResourceCommand base, String addressLine1) {
		return new CreateResourceCommand(base.categoryId(), base.name(), base.description(), addressLine1,
				base.addressLine2(), base.city(), base.province(), base.postalCode(), base.phone(), base.email(),
				base.websiteUrl(), base.costType(), base.costDetails(), base.eligibility());
	}

	private static CreateResourceCommand withCity(CreateResourceCommand base, String city) {
		return new CreateResourceCommand(base.categoryId(), base.name(), base.description(), base.addressLine1(),
				base.addressLine2(), city, base.province(), base.postalCode(), base.phone(), base.email(),
				base.websiteUrl(), base.costType(), base.costDetails(), base.eligibility());
	}

	private static UpdateResourceCommand withUpdateName(UpdateResourceCommand base, String name) {
		return new UpdateResourceCommand(base.categoryId(), name, base.description(), base.addressLine1(),
				base.addressLine2(), base.city(), base.province(), base.postalCode(), base.phone(), base.email(),
				base.websiteUrl(), base.costType(), base.costDetails(), base.eligibility());
	}

}
