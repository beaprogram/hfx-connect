package com.hfxconnect.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.AbstractPostgresIntegrationTest;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exercises the real V5 migration and the database constraints it creates,
 * against the actual {@code postgis/postgis:17-3.5} image (via the shared
 * Testcontainers singleton container) — not H2, not mocks.
 */
@Transactional
class RefreshSessionRepositoryIntegrationTest extends AbstractPostgresIntegrationTest {

	@Autowired
	private RefreshSessionRepository refreshSessionRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private DataSource dataSource;

	@Test
	void migrationCreatedTheRefreshSessionsTableWithExpectedColumns() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		List<String> columns = jdbcTemplate.queryForList(
				"SELECT column_name FROM information_schema.columns WHERE table_name = 'refresh_sessions'",
				String.class);

		assertThat(columns).containsExactlyInAnyOrder(
				"id", "user_id", "token_hash", "family_id", "expires_at", "revoked_at",
				"replaced_by_session_id", "created_at", "last_used_at");
	}

	@Test
	void userForeignKeyIsEnforced() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		UUID id = UUID.randomUUID();
		UUID nonExistentUserId = UUID.randomUUID();

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO refresh_sessions (id, user_id, token_hash, family_id, expires_at) VALUES (?, ?, ?, ?, ?)",
				id, nonExistentUserId, "hash-" + id, UUID.randomUUID(), Timestamp.from(Instant.now().plusSeconds(3600))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void tokenHashMustBeUnique() {
		User user = saveUser("token-unique-" + UUID.randomUUID() + "@example.org");
		String sharedHash = "shared-hash-" + UUID.randomUUID();
		save(user, sharedHash);

		assertThatThrownBy(() -> save(user, sharedHash))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void tokenHashIsRequiredAtTheDatabaseLevel() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		User user = saveUser("no-hash-" + UUID.randomUUID() + "@example.org");
		UUID id = UUID.randomUUID();

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO refresh_sessions (id, user_id, family_id, expires_at) VALUES (?, ?, ?, ?)",
				id, user.getId(), UUID.randomUUID(), Timestamp.from(Instant.now().plusSeconds(3600))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void expiresAtIsRequiredAtTheDatabaseLevel() {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
		User user = saveUser("no-expiry-" + UUID.randomUUID() + "@example.org");
		UUID id = UUID.randomUUID();

		assertThatThrownBy(() -> jdbcTemplate.update(
				"INSERT INTO refresh_sessions (id, user_id, token_hash, family_id) VALUES (?, ?, ?, ?)",
				id, user.getId(), "hash-" + id, UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void revokedAtDefaultsToNullAndCanBeSetLater() {
		User user = saveUser("revoke-check-" + UUID.randomUUID() + "@example.org");
		RefreshSession session = save(user, "revoke-hash-" + UUID.randomUUID());

		assertThat(session.getRevokedAt()).isNull();
		assertThat(session.isRevoked()).isFalse();

		session.revoke();
		refreshSessionRepository.saveAndFlush(session);

		RefreshSession reloaded = refreshSessionRepository.findById(session.getId()).orElseThrow();
		assertThat(reloaded.getRevokedAt()).isNotNull();
		assertThat(reloaded.isRevoked()).isTrue();
	}

	@Test
	void findByTokenHashReturnsTheMatchingSession() {
		User user = saveUser("find-hash-" + UUID.randomUUID() + "@example.org");
		String hash = "find-me-" + UUID.randomUUID();
		save(user, hash);

		assertThat(refreshSessionRepository.findByTokenHash(hash)).isPresent();
		assertThat(refreshSessionRepository.findByTokenHash("does-not-exist-" + UUID.randomUUID())).isEmpty();
	}

	@Test
	void revokeAllActiveInFamilyRevokesOnlyThatFamilysActiveSessions() {
		User user = saveUser("bulk-revoke-" + UUID.randomUUID() + "@example.org");
		UUID familyId = UUID.randomUUID();
		UUID otherFamilyId = UUID.randomUUID();
		RefreshSession first = save(user, "bulk-1-" + UUID.randomUUID(), familyId);
		RefreshSession second = save(user, "bulk-2-" + UUID.randomUUID(), familyId);
		RefreshSession otherFamily = save(user, "bulk-other-" + UUID.randomUUID(), otherFamilyId);

		int updated = refreshSessionRepository.revokeAllActiveInFamily(familyId, Instant.now());

		assertThat(updated).isEqualTo(2);
		assertThat(refreshSessionRepository.findById(first.getId()).orElseThrow().isRevoked()).isTrue();
		assertThat(refreshSessionRepository.findById(second.getId()).orElseThrow().isRevoked()).isTrue();
		assertThat(refreshSessionRepository.findById(otherFamily.getId()).orElseThrow().isRevoked()).isFalse();
	}

	@Test
	void deletingAUserCascadesToTheirRefreshSessions() {
		// Raw JDBC delete, not userRepository.delete(user): this test is
		// specifically about V5's own ON DELETE CASCADE — see ADR-008's
		// "cascading deletion" decision — not about Hibernate's unrelated
		// entity-lifecycle/cascade semantics (User has no @OneToMany back
		// to RefreshSession, so JPA-level deletion has nothing to do with
		// what this test verifies).
		User user = saveUser("cascade-check-" + UUID.randomUUID() + "@example.org");
		RefreshSession session = save(user, "cascade-hash-" + UUID.randomUUID());
		JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

		jdbcTemplate.update("DELETE FROM users WHERE id = ?", user.getId());

		Integer remaining = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM refresh_sessions WHERE id = ?", Integer.class, session.getId());
		assertThat(remaining).isZero();
	}

	private User saveUser(String email) {
		return userRepository.saveAndFlush(new User(email, email, "$2a$12$placeholderPlaceholderPlaceholderPlace"));
	}

	private RefreshSession save(User user, String tokenHash) {
		return save(user, tokenHash, UUID.randomUUID());
	}

	private RefreshSession save(User user, String tokenHash, UUID familyId) {
		return refreshSessionRepository.saveAndFlush(
				new RefreshSession(user, tokenHash, familyId, Instant.now().plusSeconds(3600)));
	}

}
