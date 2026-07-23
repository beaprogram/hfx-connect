package com.hfxconnect.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.error.InvalidPaginationException;
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

		assertThatThrownBy(() -> resourceService.create(command)).isInstanceOf(ValidationException.class);

		assertThat(resourceRepository.count()).isZero();
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

		assertThatThrownBy(() -> resourceService.create(command)).isInstanceOf(CategoryNotFoundException.class);

		assertThat(resourceRepository.count()).isZero();
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

		assertThatThrownBy(() -> resourceService.create(command)).isInstanceOf(InactiveCategoryException.class);

		assertThat(resourceRepository.count()).isZero();
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

		assertThatThrownBy(() -> resourceService.create(command)).isInstanceOf(ValidationException.class);
		assertThat(resourceRepository.count()).isZero();
	}

	@Test
	void listActiveExcludesDeactivatedResources() {
		Category category = activeCategory("List Active Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails created = resourceService.create(validCommand(category.getId(), "List Active " + marker));
		resourceService.deactivate(created.id());

		ResourcePage page = resourceService.listActive(0, 100, null);

		assertThat(page.content()).extracting(ResourceDetails::name).doesNotContain("List Active " + marker);
	}

	@Test
	void listActiveByCategoryOnlyReturnsResourcesInThatCategory() {
		Category categoryA = activeCategory("List By Category A");
		Category categoryB = activeCategory("List By Category B");
		String marker = UUID.randomUUID().toString();
		resourceService.create(validCommand(categoryA.getId(), "In Category A " + marker));
		resourceService.create(validCommand(categoryB.getId(), "In Category B " + marker));

		ResourcePage page = resourceService.listActiveByCategory(categoryA.getId(), 0, 100, null);

		assertThat(page.content()).extracting(ResourceDetails::name)
				.contains("In Category A " + marker)
				.doesNotContain("In Category B " + marker);
	}

	@Test
	void listActiveRejectsAnOutOfRangePageSize() {
		assertThatThrownBy(() -> resourceService.listActive(0, 0, null)).isInstanceOf(InvalidPaginationException.class);
		assertThatThrownBy(() -> resourceService.listActive(0, 1000, null)).isInstanceOf(InvalidPaginationException.class);
	}

	@Test
	void listActiveRejectsANegativePage() {
		assertThatThrownBy(() -> resourceService.listActive(-1, 20, null)).isInstanceOf(InvalidPaginationException.class);
	}

	@Test
	void listActiveDefaultsToNameAscending() {
		Category category = activeCategory("Default Sort Check");
		String marker = UUID.randomUUID().toString();
		resourceService.create(validCommand(category.getId(), "B Resource " + marker));
		resourceService.create(validCommand(category.getId(), "A Resource " + marker));

		ResourcePage page = resourceService.listActiveByCategory(category.getId(), 0, 100, null);

		assertThat(page.content()).extracting(ResourceDetails::name)
				.containsExactly("A Resource " + marker, "B Resource " + marker);
	}

	@Test
	void listActiveSortsByCreatedAtDescendingWhenRequested() {
		Category category = activeCategory("CreatedAt Sort Check");
		String marker = UUID.randomUUID().toString();
		ResourceDetails first = resourceService.create(validCommand(category.getId(), "First " + marker));
		ResourceDetails second = resourceService.create(validCommand(category.getId(), "Second " + marker));

		ResourcePage page = resourceService.listActiveByCategory(category.getId(), 0, 100, "createdAt");

		assertThat(page.content()).extracting(ResourceDetails::id)
				.containsSubsequence(second.id(), first.id());
	}

	@Test
	void listActiveRejectsAnUnsupportedSortValue() {
		assertThatThrownBy(() -> resourceService.listActive(0, 20, "notARealField"))
				.isInstanceOf(InvalidSortException.class);
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

	private static UpdateResourceCommand withUpdateName(UpdateResourceCommand base, String name) {
		return new UpdateResourceCommand(base.categoryId(), name, base.description(), base.addressLine1(),
				base.addressLine2(), base.city(), base.province(), base.postalCode(), base.phone(), base.email(),
				base.websiteUrl(), base.costType(), base.costDetails(), base.eligibility());
	}

}
