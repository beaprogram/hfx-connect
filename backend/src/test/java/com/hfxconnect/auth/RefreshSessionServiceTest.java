package com.hfxconnect.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hfxconnect.user.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Pure unit tests of {@link RefreshSessionService}'s rotation and
 * reuse-detection logic, with a mocked {@link RefreshSessionRepository} so
 * the exact sequence of events (which row gets revoked, which family gets
 * bulk-revoked) can be verified deterministically. Real database constraints
 * and cascade behavior are covered separately by
 * {@link RefreshSessionRepositoryIntegrationTest}.
 *
 * <p>{@link #transactionManager} is mocked but returns a real
 * {@link SimpleTransactionStatus} so {@code TransactionTemplate} (used
 * internally by {@code revokeFamilyInNewTransaction}/
 * {@code revokeSessionInNewTransaction}) can actually execute its callback —
 * this test cares that the callback runs and what it does to the mocked
 * repository, not about real transactional commit semantics, which
 * {@link AuthSessionApiIntegrationTest} verifies against the real database.
 */
@ExtendWith(MockitoExtension.class)
class RefreshSessionServiceTest {

	private static final long TTL_SECONDS = 2_592_000;

	@Mock
	private RefreshSessionRepository refreshSessionRepository;

	@Mock
	private PlatformTransactionManager transactionManager;

	private RefreshSessionService refreshSessionService;

	@BeforeEach
	void setUp() {
		refreshSessionService = new RefreshSessionService(refreshSessionRepository, TTL_SECONDS, transactionManager);
	}

	private void stubNewTransactionSupport() {
		when(transactionManager.getTransaction(any(TransactionDefinition.class)))
				.thenReturn(new SimpleTransactionStatus());
	}

	@Test
	void issueNewSessionPersistsASessionAndReturnsARawToken() {
		User user = new User("student@example.org", "student@example.org", "hash");
		when(refreshSessionRepository.saveAndFlush(any(RefreshSession.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		RefreshSessionService.IssuedRefreshToken result = refreshSessionService.issueNewSession(user);

		assertThat(result.rawToken()).isNotBlank();
		assertThat(result.expiresAt()).isAfter(Instant.now());
		verify(refreshSessionRepository).saveAndFlush(any(RefreshSession.class));
	}

	@Test
	void rotatingAValidSessionRevokesItAndCreatesAReplacementInTheSameFamily() {
		User user = new User("student@example.org", "student@example.org", "hash");
		UUID familyId = UUID.randomUUID();
		RefreshSession existing = new RefreshSession(user, "irrelevant-hash", familyId, Instant.now().plusSeconds(TTL_SECONDS));
		String rawToken = "presented-raw-token";
		when(refreshSessionRepository.findByTokenHash(RefreshTokenGenerator.hash(rawToken)))
				.thenReturn(Optional.of(existing));
		when(refreshSessionRepository.saveAndFlush(any(RefreshSession.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		RefreshSessionService.RotatedRefreshToken rotated = refreshSessionService.rotate(rawToken);

		assertThat(existing.isRevoked()).isTrue();
		assertThat(rotated.rawToken()).isNotBlank().isNotEqualTo(rawToken);
		assertThat(rotated.user()).isEqualTo(user);
		verify(refreshSessionRepository, never()).revokeAllActiveInFamily(any(), any());
	}

	@Test
	void rotatingAnUnknownTokenThrowsInvalidRefreshToken() {
		when(refreshSessionRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> refreshSessionService.rotate("unknown-token"))
				.isInstanceOf(InvalidRefreshTokenException.class);
	}

	@Test
	void rotatingAnExpiredSessionThrowsRefreshTokenExpired() {
		User user = new User("student@example.org", "student@example.org", "hash");
		RefreshSession expired = new RefreshSession(user, "hash", UUID.randomUUID(), Instant.now().minusSeconds(60));
		when(refreshSessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

		assertThatThrownBy(() -> refreshSessionService.rotate("expired-token"))
				.isInstanceOf(RefreshTokenExpiredException.class);

		verify(refreshSessionRepository, never()).saveAndFlush(any());
	}

	@Test
	void rotatingAnAlreadyRevokedSessionThrowsReuseAndRevokesTheWholeFamily() {
		stubNewTransactionSupport();
		User user = new User("student@example.org", "student@example.org", "hash");
		UUID familyId = UUID.randomUUID();
		RefreshSession alreadyRevoked = new RefreshSession(user, "hash", familyId, Instant.now().plusSeconds(TTL_SECONDS));
		alreadyRevoked.revoke();
		when(refreshSessionRepository.findByTokenHash(anyString())).thenReturn(Optional.of(alreadyRevoked));

		assertThatThrownBy(() -> refreshSessionService.rotate("reused-token"))
				.isInstanceOf(RefreshTokenReusedException.class);

		ArgumentCaptor<UUID> familyCaptor = ArgumentCaptor.forClass(UUID.class);
		verify(refreshSessionRepository).revokeAllActiveInFamily(familyCaptor.capture(), any(Instant.class));
		assertThat(familyCaptor.getValue()).isEqualTo(familyId);
		verify(refreshSessionRepository, never()).saveAndFlush(any());
	}

	@Test
	void revokeByRawTokenWithNullTokenDoesNothing() {
		refreshSessionService.revokeByRawToken(null);

		verify(refreshSessionRepository, never()).findByTokenHash(any());
	}

	@Test
	void revokeByRawTokenWithBlankTokenDoesNothing() {
		refreshSessionService.revokeByRawToken("   ");

		verify(refreshSessionRepository, never()).findByTokenHash(any());
	}

	@Test
	void revokeByRawTokenWithAnUnknownTokenDoesNotThrow() {
		when(refreshSessionRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

		refreshSessionService.revokeByRawToken("unknown-token");

		verify(refreshSessionRepository).findByTokenHash(anyString());
	}

	@Test
	void revokeByRawTokenWithAKnownTokenRevokesIt() {
		User user = new User("student@example.org", "student@example.org", "hash");
		RefreshSession session = new RefreshSession(user, "hash", UUID.randomUUID(), Instant.now().plusSeconds(TTL_SECONDS));
		String rawToken = "known-token";
		when(refreshSessionRepository.findByTokenHash(eq(RefreshTokenGenerator.hash(rawToken))))
				.thenReturn(Optional.of(session));

		refreshSessionService.revokeByRawToken(rawToken);

		assertThat(session.isRevoked()).isTrue();
	}

}
