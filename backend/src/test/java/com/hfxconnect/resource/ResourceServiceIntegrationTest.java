package com.hfxconnect.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.InvalidSearchQueryException;
import com.hfxconnect.common.error.InvalidSortException;
import com.hfxconnect.common.error.ValidationException;
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

		ResourcePage page = resourceService.search(null, null, 0, 100, null);

		assertThat(page.content()).extracting(ResourceDetails::name).doesNotContain("List Active " + marker);
	}

	@Test
	void listActiveByCategoryOnlyReturnsResourcesInThatCategory() {
		Category categoryA = activeCategory("List By Category A");
		Category categoryB = activeCategory("List By Category B");
		String marker = UUID.randomUUID().toString();
		resourceService.create(validCommand(categoryA.getId(), "In Category A " + marker));
		resourceService.create(validCommand(categoryB.getId(), "In Category B " + marker));

		ResourcePage page = resourceService.search(null, categoryA.getId(), 0, 100, null);

		assertThat(page.content()).extracting(ResourceDetails::name)
				.contains("In Category A " + marker)
				.doesNotContain("In Category B " + marker);
	}

	@Test
	void listActiveRejectsAnOutOfRangePageSize() {
		assertThatThrownBy(() -> resourceService.search(null, null, 0, 0, null)).isInstanceOf(InvalidPaginationException.class);
		assertThatThrownBy(() -> resourceService.search(null, null, 0, 1000, null)).isInstanceOf(InvalidPaginationException.class);
	}

	@Test
	void listActiveRejectsANegativePage() {
		assertThatThrownBy(() -> resourceService.search(null, null, -1, 20, null)).isInstanceOf(InvalidPaginationException.class);
	}

	@Test
	void listActiveDefaultsToNameAscending() {
		Category category = activeCategory("Default Sort Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(validCommand(category.getId(), "B Resource " + marker));
		resourceService.create(validCommand(category.getId(), "A Resource " + marker));

		ResourcePage page = resourceService.search(null, category.getId(), 0, 100, null);

		assertThat(page.content()).extracting(ResourceDetails::name)
				.containsExactly("A Resource " + marker, "B Resource " + marker);
	}

	@Test
	void listActiveSortsByCreatedAtDescendingWhenRequested() {
		Category category = activeCategory("CreatedAt Sort Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails first = resourceService.create(validCommand(category.getId(), "First " + marker));
		ResourceDetails second = resourceService.create(validCommand(category.getId(), "Second " + marker));

		ResourcePage page = resourceService.search(null, category.getId(), 0, 100, "createdAt");

		assertThat(page.content()).extracting(ResourceDetails::id)
				.containsSubsequence(second.id(), first.id());
	}

	@Test
	void listActiveRejectsAnUnsupportedSortValue() {
		assertThatThrownBy(() -> resourceService.search(null, null, 0, 20, "notARealField"))
				.isInstanceOf(InvalidSortException.class);
	}

	// ---- Keyword search (Milestone 6A) — see ADR-010 ----

	@Test
	void searchMatchesResourceName() {
		Category category = activeCategory("Search Name Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Halifax Food Bank " + marker));

		ResourcePage page = resourceService.search("Food Bank " + marker, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Halifax Food Bank " + marker);
	}

	@Test
	void searchMatchesResourceDescription() {
		Category category = activeCategory("Search Description Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withDescription(withName(validCommand(category.getId(), "Distinct Name " + marker), "Distinct Name " + marker),
				"Offers free tutoring " + marker + " for newcomers."));

		ResourcePage page = resourceService.search("tutoring " + marker, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Distinct Name " + marker);
	}

	@Test
	void searchMatchesAddressLine1() {
		Category category = activeCategory("Search Address Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withAddressLine1(withName(validCommand(category.getId(), "placeholder"), "Address Match " + marker),
				"742 Evergreen Terrace " + marker));

		ResourcePage page = resourceService.search("Evergreen Terrace " + marker, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Address Match " + marker);
	}

	@Test
	void searchMatchesCity() {
		Category category = activeCategory("Search City Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withCity(withName(validCommand(category.getId(), "placeholder"), "City Match " + marker),
				"Dartmouth" + marker));

		ResourcePage page = resourceService.search("Dartmouth" + marker, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("City Match " + marker);
	}

	@Test
	void searchIsCaseInsensitive() {
		Category category = activeCategory("Search Case Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Library Services " + marker));

		ResourcePage page = resourceService.search("LIBRARY services " + marker.toUpperCase(Locale.ROOT), null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Library Services " + marker);
	}

	@Test
	void searchReturnsAnEmptyPageWhenNothingMatches() {
		activeCategory("Search No Match Check");

		ResourcePage page = resourceService.search("no-resource-should-ever-match-this-" + UUID.randomUUID(), null, 0, 20, null);

		assertThat(page.content()).isEmpty();
		assertThat(page.totalElements()).isZero();
	}

	@Test
	void searchExcludesDeactivatedResources() {
		Category category = activeCategory("Search Deactivated Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails created = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Deactivated Search " + marker));
		resourceService.deactivate(created.id());

		ResourcePage page = resourceService.search("Deactivated Search " + marker, null, 0, 20, null);

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
		ResourcePage page = resourceService.search(marker, categoryA.getId(), 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Workshop A " + marker);
	}

	@Test
	void searchCombinedWithCategoryReturnsEmptyWhenTheCategoryHasNoMatch() {
		Category categoryA = activeCategory("Search Combo Empty A");
		Category categoryB = activeCategory("Search Combo Empty B");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withName(validCommand(categoryA.getId(), "placeholder"), "Only In A " + marker));

		ResourcePage page = resourceService.search("Only In A " + marker, categoryB.getId(), 0, 20, null);

		assertThat(page.content()).isEmpty();
	}

	@Test
	void searchPaginatesResults() {
		Category category = activeCategory("Search Pagination Check");
		String marker = UUID.randomUUID().toString();
		for (int i = 0; i < 3; i++) {
			resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Paginated " + marker + " " + i));
		}

		ResourcePage firstPage = resourceService.search("Paginated " + marker, null, 0, 2, null);
		ResourcePage secondPage = resourceService.search("Paginated " + marker, null, 1, 2, null);

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

		ResourcePage page = resourceService.search("Sorted " + marker, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name)
				.containsExactly("A Sorted " + marker, "B Sorted " + marker);
	}

	@Test
	void searchPreservesCreatedAtDescendingSortWhenRequested() {
		Category category = activeCategory("Search Sort CreatedAt Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails first = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "First Sorted " + marker));
		ResourceDetails second = resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Second Sorted " + marker));

		ResourcePage page = resourceService.search("Sorted " + marker, null, 0, 20, "createdAt");

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

		ResourcePage page = resourceService.search("50% " + marker, null, 0, 20, null);

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

		ResourcePage page = resourceService.search("user_name " + marker, null, 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Underscore Literal " + marker);
	}

	@Test
	void blankSearchQueryBehavesAsNoKeywordFilter() {
		Category category = activeCategory("Search Blank Query Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(withName(validCommand(category.getId(), "placeholder"), "Blank Query " + marker));

		ResourcePage page = resourceService.search("   ", category.getId(), 0, 20, null);

		assertThat(page.content()).extracting(ResourceDetails::name).containsExactly("Blank Query " + marker);
	}

	@Test
	void searchRejectsAQueryOverTheMaximumLength() {
		String tooLong = "a".repeat(ResourceSearchQuery.MAX_LENGTH + 1);
		assertThatThrownBy(() -> resourceService.search(tooLong, null, 0, 20, null))
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
