package com.hfxconnect.savedresource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.resource.CommunityResource;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.resource.ResourceRepository;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the real V8 migration and {@link SavedResourceRepository}
 * against the actual database — schema/constraint checks via direct JDBC
 * (the same style {@code ResourceLocationRepositoryIntegrationTest}
 * established for V7), plus every repository query's actual behavior.
 */
@Transactional
class SavedResourceRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private SavedResourceRepository savedResourceRepository;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DataSource dataSource;

	// ---- Schema/constraint checks ----

	@Test
	void migrationCreatedTheSavedResourcesTable() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		Integer tableCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM information_schema.tables WHERE table_name = 'saved_resources'", Integer.class);
		assertThat(tableCount).isEqualTo(1);
	}

	@Test
	void userForeignKeyIsEnforced() {
		CommunityResource resource = resource("FK Check Resource");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO saved_resources (user_id, resource_id) VALUES (?, ?)",
				UUID.randomUUID(), resource.getId()))
				.isInstanceOf(org.springframework.dao.DataAccessException.class);
	}

	@Test
	void resourceForeignKeyIsEnforced() {
		User user = persistUser();
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO saved_resources (user_id, resource_id) VALUES (?, ?)",
				user.getId(), UUID.randomUUID()))
				.isInstanceOf(org.springframework.dao.DataAccessException.class);
	}

	@Test
	void userResourceUniquenessIsEnforced() {
		User user = persistUser();
		CommunityResource resource = resource("Uniqueness Check Resource");
		savedResourceRepository.saveAndFlush(new SavedResource(user.getId(), resource));

		assertThatThrownBy(() -> savedResourceRepository.saveAndFlush(new SavedResource(user.getId(), resource)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void requiredFieldsAreEnforced() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO saved_resources (user_id, resource_id) VALUES (NULL, NULL)"))
				.isInstanceOf(org.springframework.dao.DataAccessException.class);
	}

	@Test
	void userDeletionCascadesToSavedResources() {
		User user = persistUser();
		CommunityResource resource = resource("User Cascade Resource");
		savedResourceRepository.saveAndFlush(new SavedResource(user.getId(), resource));

		userRepository.delete(user);
		userRepository.flush();

		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		Integer remaining = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM saved_resources WHERE user_id = ?", Integer.class, user.getId());
		assertThat(remaining).isZero();
	}

	@Test
	void resourceDeletionCascadesToSavedResources() {
		User user = persistUser();
		CommunityResource resource = resource("Resource Cascade Resource");
		savedResourceRepository.saveAndFlush(new SavedResource(user.getId(), resource));

		// Deletes the resource row directly via JDBC, bypassing Hibernate's
		// persistence context entirely — this test's goal is the real
		// database's ON DELETE CASCADE (V8), not JPA-level cascade/orphan-
		// removal semantics (which SavedResource.resource deliberately has
		// neither of; see its own Javadoc). Going through
		// ResourceRepository.delete() here would additionally require
		// Hibernate to reconcile the still-tracked SavedResource's reference
		// to the entity being removed, which is a Hibernate session-cache
		// concern this test has no interest in.
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		jdbcTemplate.update("DELETE FROM resources WHERE id = ?", resource.getId());

		Integer remaining = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM saved_resources WHERE resource_id = ?", Integer.class, resource.getId());
		assertThat(remaining).isZero();
	}

	// ---- Repository query behavior ----

	@Test
	void existsByUserIdAndResourceIdReflectsSavedState() {
		User user = persistUser();
		CommunityResource resource = resource("Exists Check Resource");
		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(user.getId(), resource.getId())).isFalse();

		savedResourceRepository.saveAndFlush(new SavedResource(user.getId(), resource));

		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(user.getId(), resource.getId())).isTrue();
	}

	@Test
	void sameResourceCanBeSavedByTwoDifferentUsers() {
		User userA = persistUser();
		User userB = persistUser();
		CommunityResource resource = resource("Shared Save Resource");

		savedResourceRepository.saveAndFlush(new SavedResource(userA.getId(), resource));
		savedResourceRepository.saveAndFlush(new SavedResource(userB.getId(), resource));

		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(userA.getId(), resource.getId())).isTrue();
		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(userB.getId(), resource.getId())).isTrue();
	}

	@Test
	void deleteByUserIdAndResourceIdRemovesOnlyThatUsersRelation() {
		User userA = persistUser();
		User userB = persistUser();
		CommunityResource resource = resource("Isolated Delete Resource");
		savedResourceRepository.saveAndFlush(new SavedResource(userA.getId(), resource));
		savedResourceRepository.saveAndFlush(new SavedResource(userB.getId(), resource));

		savedResourceRepository.deleteByUserIdAndResource_Id(userA.getId(), resource.getId());
		savedResourceRepository.flush();

		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(userA.getId(), resource.getId())).isFalse();
		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(userB.getId(), resource.getId())).isTrue();
	}

	@Test
	void deleteByUserIdAndResourceIdOnAnAbsentRelationIsANoOp() {
		User user = persistUser();
		CommunityResource resource = resource("Absent Delete Resource");

		savedResourceRepository.deleteByUserIdAndResource_Id(user.getId(), resource.getId());

		assertThat(savedResourceRepository.existsByUserIdAndResource_Id(user.getId(), resource.getId())).isFalse();
	}

	@Test
	void findSavedResourceIdsReturnsOnlyTheCurrentUsersSavedIdsAmongCandidates() {
		User userA = persistUser();
		User userB = persistUser();
		CommunityResource savedByA = resource("Status Saved By A");
		CommunityResource savedByB = resource("Status Saved By B");
		CommunityResource savedByNeither = resource("Status Saved By Neither");
		savedResourceRepository.saveAndFlush(new SavedResource(userA.getId(), savedByA));
		savedResourceRepository.saveAndFlush(new SavedResource(userB.getId(), savedByB));

		List<UUID> result = savedResourceRepository.findSavedResourceIds(
				userA.getId(), List.of(savedByA.getId(), savedByB.getId(), savedByNeither.getId()));

		assertThat(result).containsExactly(savedByA.getId());
	}

	@Test
	void findActiveSavedResourcesExcludesAnotherUsersSaves() {
		User userA = persistUser();
		User userB = persistUser();
		CommunityResource resourceA = resource("Isolation List A");
		CommunityResource resourceB = resource("Isolation List B");
		savedResourceRepository.saveAndFlush(new SavedResource(userA.getId(), resourceA));
		savedResourceRepository.saveAndFlush(new SavedResource(userB.getId(), resourceB));

		Page<SavedResource> page = savedResourceRepository.findActiveSavedResourcesByUserIdOrderBySavedAtDesc(
				userA.getId(), PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(sr -> sr.getResource().getId()).containsExactly(resourceA.getId());
	}

	@Test
	void findActiveSavedResourcesExcludesADeactivatedResource() {
		User user = persistUser();
		CommunityResource resource = resource("Deactivated List Check");
		savedResourceRepository.saveAndFlush(new SavedResource(user.getId(), resource));
		resource.deactivate();
		resourceRepository.saveAndFlush(resource);

		Page<SavedResource> page = savedResourceRepository.findActiveSavedResourcesByUserIdOrderBySavedAtDesc(
				user.getId(), PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(sr -> sr.getResource().getId()).doesNotContain(resource.getId());
	}

	@Test
	void findActiveSavedResourcesOrdersNewestSavedFirstByDefault() {
		User user = persistUser();
		CommunityResource first = resource("Ordering First Saved");
		CommunityResource second = resource("Ordering Second Saved");
		savedResourceRepository.saveAndFlush(new SavedResource(user.getId(), first));
		savedResourceRepository.saveAndFlush(new SavedResource(user.getId(), second));

		Page<SavedResource> page = savedResourceRepository.findActiveSavedResourcesByUserIdOrderBySavedAtDesc(
				user.getId(), PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(sr -> sr.getResource().getId())
				.containsExactly(second.getId(), first.getId());
	}

	@Test
	void findActiveSavedResourcesByNameOrdersAscendingByResourceName() {
		User user = persistUser();
		CommunityResource zebra = resourceNamed("Zebra Sort Check");
		CommunityResource apple = resourceNamed("Apple Sort Check");
		savedResourceRepository.saveAndFlush(new SavedResource(user.getId(), zebra));
		savedResourceRepository.saveAndFlush(new SavedResource(user.getId(), apple));

		Page<SavedResource> page = savedResourceRepository.findActiveSavedResourcesByUserIdOrderByResourceName(
				user.getId(), PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(sr -> sr.getResource().getId())
				.containsExactly(apple.getId(), zebra.getId());
	}

	@Test
	void findActiveSavedResourcesPaginatesCorrectly() {
		User user = persistUser();
		String marker = UUID.randomUUID().toString();
		for (int i = 0; i < 3; i++) {
			savedResourceRepository.saveAndFlush(new SavedResource(user.getId(), resource("Pagination " + marker + " " + i)));
		}

		Page<SavedResource> firstPage = savedResourceRepository.findActiveSavedResourcesByUserIdOrderBySavedAtDesc(
				user.getId(), PageRequest.of(0, 2));

		assertThat(firstPage.getContent()).hasSize(2);
		assertThat(firstPage.getTotalElements()).isGreaterThanOrEqualTo(3);
		assertThat(firstPage.getTotalPages()).isGreaterThanOrEqualTo(2);
	}

	// ---- Helpers ----

	private User persistUser() {
		return userRepository.saveAndFlush(TestUserFactory.withRole(com.hfxconnect.user.Role.USER));
	}

	private CommunityResource resource(String namePrefix) {
		return resourceNamed(namePrefix);
	}

	private CommunityResource resourceNamed(String name) {
		String marker = UUID.randomUUID().toString();
		Category category = category(name);
		CommunityResource resource = new CommunityResource(category, name + " " + marker, "sr-" + marker,
				"A helpful community resource.", "123 Main St", null, "Halifax", "NS", "B3H 4R2", null, null, null,
				CostType.UNKNOWN, null, null);
		return resourceRepository.saveAndFlush(resource);
	}

	private Category category(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " Category " + marker;
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "sr-cat-" + marker, null));
	}

}
