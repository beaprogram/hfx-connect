package com.hfxconnect.resourcesubmission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.category.Category;
import com.hfxconnect.category.CategoryRepository;
import com.hfxconnect.resource.CostType;
import com.hfxconnect.user.Role;
import com.hfxconnect.user.TestUserFactory;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the real V9 migration and {@link ResourceSubmissionRepository}
 * against the actual database, following {@code SavedResourceRepositoryIntegrationTest}'s
 * established pattern (Milestone 8A).
 */
@Transactional
class ResourceSubmissionRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private ResourceSubmissionRepository submissionRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DataSource dataSource;

	@Test
	void migrationCreatedTheResourceSubmissionsTable() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		Integer tableCount = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM information_schema.tables WHERE table_name = 'resource_submissions'", Integer.class);
		assertThat(tableCount).isEqualTo(1);
	}

	@Test
	void userForeignKeyIsEnforced() {
		Category category = category("FK Check");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO resource_submissions (id, submitted_by_user_id, category_id, name, normalized_name, "
						+ "short_description, address_line_1, city, province, postal_code, cost_type) "
						+ "VALUES (?, ?, ?, 'X', 'x', 'short', 'addr', 'Halifax', 'NS', 'B3H 4R2', 'UNKNOWN')",
				UUID.randomUUID(), UUID.randomUUID(), category.getId()))
				.isInstanceOf(DataAccessException.class);
	}

	@Test
	void categoryForeignKeyIsEnforced() {
		User user = persistUser();
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO resource_submissions (id, submitted_by_user_id, category_id, name, normalized_name, "
						+ "short_description, address_line_1, city, province, postal_code, cost_type) "
						+ "VALUES (?, ?, ?, 'X', 'x', 'short', 'addr', 'Halifax', 'NS', 'B3H 4R2', 'UNKNOWN')",
				UUID.randomUUID(), user.getId(), 987654321L))
				.isInstanceOf(DataAccessException.class);
	}

	@Test
	void statusCheckConstraintIsEnforced() {
		User user = persistUser();
		Category category = category("Status Check");
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO resource_submissions (id, submitted_by_user_id, category_id, name, normalized_name, "
						+ "short_description, address_line_1, city, province, postal_code, cost_type, status) "
						+ "VALUES (?, ?, ?, 'X', 'x', 'short', 'addr', 'Halifax', 'NS', 'B3H 4R2', 'UNKNOWN', 'NOT_A_REAL_STATUS')",
				UUID.randomUUID(), user.getId(), category.getId()))
				.isInstanceOf(DataAccessException.class);
	}

	@Test
	void duplicatePendingSubmissionForSameUserCategoryAndNameIsRejected() {
		User user = persistUser();
		Category category = category("Duplicate Check");

		submissionRepository.saveAndFlush(submission(user.getId(), category, "Same Name", "same name"));

		assertThatThrownBy(() -> submissionRepository.saveAndFlush(
				submission(user.getId(), category, "Same Name", "same name")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void nonPendingDuplicatesAreAllowed() {
		User user = persistUser();
		Category category = category("Withdrawn Duplicate Check");

		ResourceSubmission first = submissionRepository.saveAndFlush(submission(user.getId(), category, "Dup Name", "dup name"));
		first.withdraw();
		submissionRepository.saveAndFlush(first);

		ResourceSubmission second = submissionRepository.saveAndFlush(submission(user.getId(), category, "Dup Name", "dup name"));

		assertThat(second.getId()).isNotEqualTo(first.getId());
	}

	@Test
	void differentUsersCanSubmitTheSameNameAndCategorySimultaneously() {
		User userA = persistUser();
		User userB = persistUser();
		Category category = category("Cross User Check");

		submissionRepository.saveAndFlush(submission(userA.getId(), category, "Shared Name", "shared name"));
		ResourceSubmission second = submissionRepository.saveAndFlush(submission(userB.getId(), category, "Shared Name", "shared name"));

		assertThat(second.getId()).isNotNull();
	}

	@Test
	void findByOwnerOrderBySubmittedAtDescExcludesOtherUsers() {
		User owner = persistUser();
		User other = persistUser();
		Category category = category("Owner Scope Check");
		String marker = UUID.randomUUID().toString();
		submissionRepository.saveAndFlush(submission(owner.getId(), category, "Owned " + marker, "owned " + marker));
		submissionRepository.saveAndFlush(submission(other.getId(), category, "Not Owned " + marker, "not owned " + marker));

		Page<ResourceSubmission> page = submissionRepository.findByOwnerOrderBySubmittedAtDesc(owner.getId(), PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(ResourceSubmission::getName).containsExactly("Owned " + marker);
	}

	@Test
	void findByOwnerOrderBySubmittedAtDescOrdersNewestFirst() {
		User owner = persistUser();
		Category category = category("Order Check");
		String marker = UUID.randomUUID().toString();
		ResourceSubmission first = submissionRepository.saveAndFlush(submission(owner.getId(), category, "First " + marker, "first " + marker));
		ResourceSubmission second = submissionRepository.saveAndFlush(submission(owner.getId(), category, "Second " + marker, "second " + marker));

		Page<ResourceSubmission> page = submissionRepository.findByOwnerOrderBySubmittedAtDesc(owner.getId(), PageRequest.of(0, 20));

		assertThat(page.getContent()).extracting(ResourceSubmission::getId).containsExactly(second.getId(), first.getId());
	}

	@Test
	void findByIdAndOwnerReturnsEmptyForAnotherUsersSubmission() {
		User owner = persistUser();
		User other = persistUser();
		Category category = category("Ownership Detail Check");
		ResourceSubmission submission = submissionRepository.saveAndFlush(submission(owner.getId(), category, "Mine", "mine"));

		assertThat(submissionRepository.findByIdAndOwner(submission.getId(), other.getId())).isEmpty();
		assertThat(submissionRepository.findByIdAndOwner(submission.getId(), owner.getId())).isPresent();
	}

	@Test
	void pagingReturnsCorrectPage() {
		User owner = persistUser();
		Category category = category("Paging Check");
		String marker = UUID.randomUUID().toString();
		for (int i = 0; i < 3; i++) {
			submissionRepository.saveAndFlush(submission(owner.getId(), category, "Item " + i + " " + marker, "item " + i + " " + marker));
		}

		Page<ResourceSubmission> page = submissionRepository.findByOwnerOrderBySubmittedAtDesc(owner.getId(), PageRequest.of(0, 2));

		assertThat(page.getContent()).hasSize(2);
		assertThat(page.getTotalElements()).isEqualTo(3);
		assertThat(page.getTotalPages()).isEqualTo(2);
	}

	private ResourceSubmission submission(UUID userId, Category category, String name, String normalizedName) {
		return new ResourceSubmission(userId, category, name, normalizedName, "A short description.", null,
				"123 Main St", null, "Halifax", "NS", "B3H 4R2", null, null, null, CostType.UNKNOWN, null, null);
	}

	private User persistUser() {
		return userRepository.saveAndFlush(TestUserFactory.withRole(Role.USER));
	}

	private Category category(String namePrefix) {
		String marker = UUID.randomUUID().toString();
		String name = namePrefix + " " + marker;
		return categoryRepository.save(new Category(name, name.toLowerCase(Locale.ROOT), "rs-cat-" + marker, null));
	}

}
