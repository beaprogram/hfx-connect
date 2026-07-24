package com.hfxconnect.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the real V4 migration and the database constraints it creates,
 * against the actual {@code postgis/postgis:17-3.5} image (via the shared
 * Testcontainers singleton container) — not H2, not mocks.
 *
 * <p>{@code @Transactional} rolls each test method back automatically, since
 * every test here runs entirely on the test thread (no separate HTTP request
 * thread is involved, unlike {@link UserApiIntegrationTest}).
 */
@Transactional
class UserRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DataSource dataSource;

	@Test
	void migrationCreatedTheUsersTableWithExpectedColumns() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		List<String> columns = jdbcTemplate.queryForList(
				"SELECT column_name FROM information_schema.columns WHERE table_name = 'users'",
				String.class);

		assertThat(columns).containsExactlyInAnyOrder(
				"id", "email", "normalized_email", "password_hash", "role", "status",
				"email_verified", "created_at", "updated_at");
	}

	@Test
	void emailIsRequiredAtTheDatabaseLevel() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		UUID id = UUID.randomUUID();

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO users (id, normalized_email, password_hash) VALUES (?, ?, ?)",
				id, "no-email-" + id + "@example.org", "hash"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void passwordHashIsRequiredAtTheDatabaseLevel() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		UUID id = UUID.randomUUID();

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO users (id, email, normalized_email) VALUES (?, ?, ?)",
				id, "no-hash-" + id + "@example.org", "no-hash-" + id + "@example.org"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void normalizedEmailMustBeUnique() {
		String normalizedEmail = "unique-check-" + UUID.randomUUID() + "@example.org";
		save(normalizedEmail, normalizedEmail);

		assertThatThrownBy(() -> save(normalizedEmail, normalizedEmail))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void roleMustBeAValidValue() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		UUID id = UUID.randomUUID();
		String email = "bad-role-" + id + "@example.org";

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO users (id, email, normalized_email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
				id, email, email, "hash", "SUPERUSER"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void statusMustBeAValidValue() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		UUID id = UUID.randomUUID();
		String email = "bad-status-" + id + "@example.org";

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO users (id, email, normalized_email, password_hash, status) VALUES (?, ?, ?, ?, ?)",
				id, email, email, "hash", "ON_VACATION"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void defaultRoleStatusAndEmailVerifiedAreAppliedWhenCreatedThroughTheEntityConstructor() {
		String email = "defaults-" + UUID.randomUUID() + "@example.org";
		User saved = save(email, email);

		assertThat(saved.getRole()).isEqualTo(Role.USER);
		assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(saved.isEmailVerified()).isFalse();
	}

	@Test
	void createdAtAndUpdatedAtAreSetOnInsert() {
		Instant before = Instant.now().minusSeconds(1);
		String email = "timestamp-check-" + UUID.randomUUID() + "@example.org";

		User saved = save(email, email);

		assertThat(saved.getCreatedAt()).isAfter(before);
		assertThat(saved.getUpdatedAt()).isAfter(before);
	}

	@Test
	void findByNormalizedEmailReturnsTheMatchingUser() {
		String email = "find-check-" + UUID.randomUUID() + "@example.org";
		save(email, email);

		Optional<User> found = userRepository.findByNormalizedEmail(email);

		assertThat(found).isPresent();
		assertThat(found.get().getNormalizedEmail()).isEqualTo(email);
	}

	@Test
	void findByNormalizedEmailIsEmptyWhenNoUserHasThatEmail() {
		assertThat(userRepository.findByNormalizedEmail("does-not-exist-" + UUID.randomUUID() + "@example.org"))
				.isEmpty();
	}

	@Test
	void existsByNormalizedEmailIsCaseInsensitiveInPractice() {
		String normalizedEmail = "case-check-" + UUID.randomUUID() + "@example.org";
		save(normalizedEmail, normalizedEmail);

		// The repository method itself does a literal comparison — case
		// insensitivity is achieved by RegistrationService always normalizing
		// to lowercase before calling it, exercised here directly to confirm
		// the stored normalized_email really is queryable by that same value.
		assertThat(userRepository.existsByNormalizedEmail(normalizedEmail)).isTrue();
		assertThat(userRepository.existsByNormalizedEmail(normalizedEmail.toUpperCase())).isFalse();
	}

	private User save(String email, String normalizedEmail) {
		// saveAndFlush (not save): User.id is a Hibernate-generated UUID, so a
		// plain save() does not force the INSERT synchronously — the
		// constraint-violation tests below need the flush to happen inside
		// this call to catch it. See ADR-007 / ResourceService.create()'s
		// identical reasoning.
		return userRepository.saveAndFlush(new User(email, normalizedEmail, "$2a$12$placeholderPlaceholderPlaceholderPlace"));
	}

}
