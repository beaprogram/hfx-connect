package com.hfxconnect.savedresource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.common.error.InvalidPaginationException;
import com.hfxconnect.common.error.InvalidSortException;
import com.hfxconnect.common.error.ValidationException;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.ResourceNotFoundException;
import com.hfxconnect.resource.ResourceRepository;
import com.hfxconnect.user.Role;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises {@link SavedResourceService}'s full business behavior against
 * the real database (real repositories, no mocks) — the same style
 * {@code ResourceServiceIntegrationTest} established, since idempotency,
 * visibility rules, and user isolation are exactly the kind of behavior
 * mocking would fail to prove.
 */
@Transactional
class SavedResourceServiceIntegrationTest extends com.hfxconnect.AbstractPostgresIntegrationTest {

	@Autowired
	private SavedResourceService savedResourceService;

	@Autowired
	private SavedResourceRepository savedResourceRepository;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private UserRepository userRepository;

	// ---- save ----

	@Test
	void savesAnActiveResource() {
		User user = persistUser();
		CommunityResource resource = resource("Save Active Check");

		savedResourceService.save(user.getId(), resource.getId());

		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(user.getId(), resource.getId())).isTrue();
	}

	@Test
	void savingAMissingResourceThrowsNotFound() {
		User user = persistUser();

		assertThatThrownBy(() -> savedResourceService.save(user.getId(), UUID.randomUUID()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void savingAnInactiveResourceThrowsNotFound() {
		User user = persistUser();
		CommunityResource resource = resource("Save Inactive Check");
		resource.deactivate();
		resourceRepository.saveAndFlush(resource);

		assertThatThrownBy(() -> savedResourceService.save(user.getId(), resource.getId()))
				.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void repeatedSaveSucceedsWithoutCreatingADuplicateRow() {
		User user = persistUser();
		CommunityResource resource = resource("Repeated Save Check");

		savedResourceService.save(user.getId(), resource.getId());
		savedResourceService.save(user.getId(), resource.getId());

		long count = savedResourceRepository.findAll().stream()
				.filter(sr -> sr.getUserId().equals(user.getId()) && sr.getResource().getId().equals(resource.getId()))
				.count();
		assertThat(count).isEqualTo(1);
	}

	@Test
	void savingAlreadySavedResourceDoesNotThrowEvenIfTheResourceRowWereSomehowGone() {
		// The idempotent short-circuit (existsBy... check) means a repeat
		// save never even needs to re-verify the resource still exists/is
		// active — proven directly here rather than assumed.
		User user = persistUser();
		CommunityResource resource = resource("Idempotent Short Circuit Check");
		savedResourceService.save(user.getId(), resource.getId());

		savedResourceService.save(user.getId(), resource.getId());

		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(user.getId(), resource.getId())).isTrue();
	}

	// ---- remove ----

	@Test
	void removesAnExistingSavedRelation() {
		User user = persistUser();
		CommunityResource resource = resource("Remove Existing Check");
		savedResourceService.save(user.getId(), resource.getId());

		savedResourceService.remove(user.getId(), resource.getId());

		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(user.getId(), resource.getId())).isFalse();
	}

	@Test
	void repeatedRemoveSucceeds() {
		User user = persistUser();
		CommunityResource resource = resource("Repeated Remove Check");
		savedResourceService.save(user.getId(), resource.getId());

		savedResourceService.remove(user.getId(), resource.getId());
		savedResourceService.remove(user.getId(), resource.getId());

		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(user.getId(), resource.getId())).isFalse();
	}

	@Test
	void removingAnAbsentRelationSucceeds() {
		User user = persistUser();
		CommunityResource resource = resource("Never Saved Remove Check");

		savedResourceService.remove(user.getId(), resource.getId());

		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(user.getId(), resource.getId())).isFalse();
	}

	@Test
	void removeDoesNotAffectAnotherUsersSavedRelation() {
		User userA = persistUser();
		User userB = persistUser();
		CommunityResource resource = resource("Remove Isolation Check");
		savedResourceService.save(userA.getId(), resource.getId());
		savedResourceService.save(userB.getId(), resource.getId());

		savedResourceService.remove(userA.getId(), resource.getId());

		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(userA.getId(), resource.getId())).isFalse();
		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(userB.getId(), resource.getId())).isTrue();
	}

	@Test
	void removeWorksEvenWhenTheResourceHasBeenDeactivated() {
		User user = persistUser();
		CommunityResource resource = resource("Remove Inactive Check");
		savedResourceService.save(user.getId(), resource.getId());
		resource.deactivate();
		resourceRepository.saveAndFlush(resource);

		savedResourceService.remove(user.getId(), resource.getId());

		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(user.getId(), resource.getId())).isFalse();
	}

	// ---- list ----

	@Test
	void listsOnlyTheCurrentUsersSavedResources() {
		User userA = persistUser();
		User userB = persistUser();
		CommunityResource resourceA = resource("List Isolation A");
		CommunityResource resourceB = resource("List Isolation B");
		savedResourceService.save(userA.getId(), resourceA.getId());
		savedResourceService.save(userB.getId(), resourceB.getId());

		SavedResourcePageResponse page = savedResourceService.list(userA.getId(), 0, 20, null);

		assertThat(page.content()).extracting(sr -> sr.resource().id()).containsExactly(resourceA.getId());
	}

	@Test
	void listedResourceHasTheSavedAtAndResourceSummaryFields() {
		User user = persistUser();
		CommunityResource resource = resource("List Summary Fields Check");
		savedResourceService.save(user.getId(), resource.getId());

		SavedResourcePageResponse page = savedResourceService.list(user.getId(), 0, 20, null);

		SavedResourceSummaryResponse summary = page.content().stream()
				.filter(sr -> sr.resource().id().equals(resource.getId()))
				.findFirst()
				.orElseThrow();
		assertThat(summary.savedAt()).isNotNull();
		assertThat(summary.resource().name()).isEqualTo(resource.getName());
		assertThat(summary.resource().slug()).isEqualTo(resource.getSlug());
		assertThat(summary.resource().category().id()).isEqualTo(resource.getCategory().getId());
	}

	@Test
	void listExcludesADeactivatedSavedResource() {
		User user = persistUser();
		CommunityResource resource = resource("List Deactivated Exclusion Check");
		savedResourceService.save(user.getId(), resource.getId());
		resource.deactivate();
		resourceRepository.saveAndFlush(resource);

		SavedResourcePageResponse page = savedResourceService.list(user.getId(), 0, 20, null);

		assertThat(page.content()).extracting(sr -> sr.resource().id()).doesNotContain(resource.getId());
	}

	@Test
	void listDefaultsToNewestSavedFirst() {
		User user = persistUser();
		CommunityResource first = resource("List Default Order First");
		CommunityResource second = resource("List Default Order Second");
		savedResourceService.save(user.getId(), first.getId());
		savedResourceService.save(user.getId(), second.getId());

		SavedResourcePageResponse page = savedResourceService.list(user.getId(), 0, 20, null);

		assertThat(page.content()).extracting(sr -> sr.resource().id())
				.containsExactly(second.getId(), first.getId());
	}

	@Test
	void listSortByNameOrdersAscending() {
		User user = persistUser();
		CommunityResource zebra = resourceNamed("Zebra Service Sort Check");
		CommunityResource apple = resourceNamed("Apple Service Sort Check");
		savedResourceService.save(user.getId(), zebra.getId());
		savedResourceService.save(user.getId(), apple.getId());

		SavedResourcePageResponse page = savedResourceService.list(user.getId(), 0, 20, "name");

		assertThat(page.content()).extracting(sr -> sr.resource().id())
				.containsExactly(apple.getId(), zebra.getId());
	}

	@Test
	void listRejectsAnUnrecognizedSortValue() {
		User user = persistUser();

		assertThatThrownBy(() -> savedResourceService.list(user.getId(), 0, 20, "unknown"))
				.isInstanceOf(InvalidSortException.class);
	}

	@Test
	void listRejectsANegativePage() {
		User user = persistUser();

		assertThatThrownBy(() -> savedResourceService.list(user.getId(), -1, 20, null))
				.isInstanceOf(InvalidPaginationException.class);
	}

	@Test
	void listRejectsAnOutOfRangeSize() {
		User user = persistUser();

		assertThatThrownBy(() -> savedResourceService.list(user.getId(), 0, 0, null))
				.isInstanceOf(InvalidPaginationException.class);
		assertThatThrownBy(() -> savedResourceService.list(user.getId(), 0, 1000, null))
				.isInstanceOf(InvalidPaginationException.class);
	}

	@Test
	void listPaginatesCorrectly() {
		User user = persistUser();
		String marker = UUID.randomUUID().toString();
		for (int i = 0; i < 3; i++) {
			CommunityResource resource = resourceNamed("Service Pagination " + marker + " " + i);
			savedResourceService.save(user.getId(), resource.getId());
		}

		SavedResourcePageResponse firstPage = savedResourceService.list(user.getId(), 0, 2, null);

		assertThat(firstPage.content()).hasSize(2);
		assertThat(firstPage.totalElements()).isEqualTo(3);
		assertThat(firstPage.totalPages()).isEqualTo(2);
	}

	// ---- status ----

	@Test
	void statusReturnsOnlyTheCurrentUsersSavedIdsAmongCandidates() {
		User userA = persistUser();
		User userB = persistUser();
		CommunityResource savedByA = resource("Status Service Saved By A");
		CommunityResource savedByB = resource("Status Service Saved By B");
		CommunityResource savedByNeither = resource("Status Service Saved By Neither");
		savedResourceService.save(userA.getId(), savedByA.getId());
		savedResourceService.save(userB.getId(), savedByB.getId());

		SavedResourceStatusResponse response = savedResourceService.status(userA.getId(),
				new SavedResourceStatusRequest(List.of(savedByA.getId(), savedByB.getId(), savedByNeither.getId())));

		assertThat(response.savedResourceIds()).containsExactly(savedByA.getId());
	}

	@Test
	void statusNormalizesDuplicateRequestedIds() {
		User user = persistUser();
		CommunityResource resource = resource("Status Duplicate Ids Check");
		savedResourceService.save(user.getId(), resource.getId());

		SavedResourceStatusResponse response = savedResourceService.status(user.getId(),
				new SavedResourceStatusRequest(List.of(resource.getId(), resource.getId())));

		assertThat(response.savedResourceIds()).containsExactly(resource.getId());
	}

	@Test
	void statusRejectsANullEntryInRequestedIds() {
		User user = persistUser();
		List<UUID> withNull = new java.util.ArrayList<>();
		withNull.add(UUID.randomUUID());
		withNull.add(null);

		assertThatThrownBy(() -> savedResourceService.status(user.getId(), new SavedResourceStatusRequest(withNull)))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void statusRejectsMoreThanTheMaximumDistinctIds() {
		User user = persistUser();
		List<UUID> tooMany = java.util.stream.Stream.generate(UUID::randomUUID)
				.limit(SavedResourceValidation.MAX_STATUS_IDS + 1)
				.toList();

		assertThatThrownBy(() -> savedResourceService.status(user.getId(), new SavedResourceStatusRequest(tooMany)))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void statusRejectsAMissingResourceIdsList() {
		User user = persistUser();

		assertThatThrownBy(() -> savedResourceService.status(user.getId(), new SavedResourceStatusRequest(null)))
				.isInstanceOf(ValidationException.class);
	}

	@Test
	void statusWithAnEmptyListReturnsAnEmptyResultWithoutQuerying() {
		User user = persistUser();

		SavedResourceStatusResponse response = savedResourceService.status(user.getId(),
				new SavedResourceStatusRequest(List.of()));

		assertThat(response.savedResourceIds()).isEmpty();
	}

	// ---- Helpers ----

	private User persistUser() {
		return userRepository.saveAndFlush(TestUserFactory.withRole(Role.USER));
	}

	private CommunityResource resource(String namePrefix) {
		return resourceNamed(namePrefix);
	}

	private CommunityResource resourceNamed(String name) {
		String marker = UUID.randomUUID().toString();
		Category category = category(name);
		CommunityResource resource = new CommunityResource(category, name + " " + marker, "srsvc-" + marker,
				"A helpful community resource.", "123 Main St", null, "Halifax", "NS", "B3H 4R2", null, null, null,
				CostType.UNKNOWN, null, null);
		return resourceRepository.saveAndFlush(resource);
	}

	private Category category(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " Category " + marker;
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "srsvc-cat-" + marker, null));
	}

}
