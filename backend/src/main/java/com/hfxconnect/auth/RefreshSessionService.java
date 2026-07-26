package com.hfxconnect.auth;

import com.hfxconnect.user.AccountStatus;
import com.hfxconnect.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * All refresh-session business logic: issuing new sessions, rotating them on
 * every successful refresh, detecting and responding to reuse, and
 * revocation (logout). See ADR-008 for the full rotation/reuse-detection
 * design; this class is where that design is actually implemented.
 */
@Service
public class RefreshSessionService {

	private final RefreshSessionRepository refreshSessionRepository;
	private final long refreshTokenTtlSeconds;
	private final TransactionTemplate requiresNewTransactionTemplate;

	public RefreshSessionService(
			RefreshSessionRepository refreshSessionRepository,
			@Value("${app.jwt.refresh-token-ttl-seconds}") long refreshTokenTtlSeconds,
			PlatformTransactionManager transactionManager) {
		this.refreshSessionRepository = refreshSessionRepository;
		this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
		this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
		this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
	}

	public record IssuedRefreshToken(String rawToken, Instant expiresAt) {
	}

	public record RotatedRefreshToken(String rawToken, Instant expiresAt, User user) {
	}

	/** A brand-new login: starts a new rotation family. */
	@Transactional
	public IssuedRefreshToken issueNewSession(User user) {
		UUID familyId = UUID.randomUUID();
		return createSession(user, familyId).issued();
	}

	/**
	 * The full refresh sequence: hash the presented token, locate its
	 * session, validate it, and — only if valid — revoke it and create its
	 * replacement in the same family. See ADR-008's "Reuse detection" and
	 * "Concurrency" sections for why an already-revoked session presented
	 * again revokes the whole family rather than just failing quietly.
	 *
	 * <p>The reuse-detection and account-unavailable branches both revoke a
	 * session (or a whole family) and then throw — a write that must survive
	 * even though this method is about to signal failure. A plain
	 * {@code @Transactional} method here would roll that write back along
	 * with everything else when the exception propagates (Spring's default
	 * behavior for any unchecked exception); {@code noRollbackFor} was tried
	 * first and, verified empirically against the real running app and
	 * database (not just this class's own mocked unit tests, which cannot
	 * exercise real transaction demarcation at all), did not prevent the
	 * rollback in this project's stack. Both revocations are therefore
	 * committed in their own independent, immediately-committing
	 * {@code PROPAGATION_REQUIRES_NEW} transaction — see
	 * {@link #revokeFamilyInNewTransaction} and
	 * {@link #revokeSessionInNewTransaction} — before the exception is
	 * thrown, so they persist regardless of what happens afterward in this
	 * method's own (read-only, by that point) transaction.
	 */
	@Transactional
	public RotatedRefreshToken rotate(String presentedRawToken) {
		String tokenHash = RefreshTokenGenerator.hash(presentedRawToken);
		RefreshSession session = refreshSessionRepository.findByTokenHash(tokenHash)
				.orElseThrow(InvalidRefreshTokenException::new);

		if (session.isRevoked()) {
			// Either already rotated away by an earlier refresh, already
			// revoked by a logout, or a genuine stolen-token replay. Every
			// one of those cases means this exact token must never be
			// trusted again — and since we can no longer tell which case it
			// is, the safe response is to kill the entire family.
			revokeFamilyInNewTransaction(session.getFamilyId());
			throw new RefreshTokenReusedException();
		}
		if (session.isExpired()) {
			throw new RefreshTokenExpiredException();
		}

		User user = session.getUser();
		if (user.getStatus() != AccountStatus.ACTIVE) {
			// Checked before rotation, not after: an account that has
			// become unavailable since its last refresh (e.g. suspended)
			// must not be handed a fresh session — but the presented token
			// is still consumed (revoked) so it can't be retried either.
			revokeSessionInNewTransaction(session.getId());
			throw new AccountUnavailableException();
		}

		session.markUsed();
		CreatedSession replacement = createSession(user, session.getFamilyId());
		session.replaceWith(replacement.entity().getId());

		return new RotatedRefreshToken(replacement.issued().rawToken(), replacement.issued().expiresAt(), user);
	}

	/**
	 * Idempotent and safe by design: a missing or unknown token simply does
	 * nothing (no exception, no signal to the caller about whether a session
	 * existed) — see {@code LogoutService}, which never needs to know.
	 */
	@Transactional
	public void revokeByRawToken(String presentedRawToken) {
		if (presentedRawToken == null || presentedRawToken.isBlank()) {
			return;
		}
		String tokenHash = RefreshTokenGenerator.hash(presentedRawToken);
		Optional<RefreshSession> session = refreshSessionRepository.findByTokenHash(tokenHash);
		session.ifPresent(RefreshSession::revoke);
	}

	/** Revokes every still-active session in a family, committed independently of the caller's own transaction. */
	private void revokeFamilyInNewTransaction(UUID familyId) {
		requiresNewTransactionTemplate.executeWithoutResult(
				status -> refreshSessionRepository.revokeAllActiveInFamily(familyId, Instant.now()));
	}

	/** Revokes exactly one session by id, committed independently of the caller's own transaction. */
	private void revokeSessionInNewTransaction(UUID sessionId) {
		requiresNewTransactionTemplate.executeWithoutResult(status ->
				refreshSessionRepository.findById(sessionId).ifPresent(RefreshSession::revoke));
	}

	private record CreatedSession(IssuedRefreshToken issued, RefreshSession entity) {
	}

	private CreatedSession createSession(User user, UUID familyId) {
		String rawToken = RefreshTokenGenerator.generateRawToken();
		String tokenHash = RefreshTokenGenerator.hash(rawToken);
		Instant expiresAt = Instant.now().plus(Duration.ofSeconds(refreshTokenTtlSeconds));

		RefreshSession session = new RefreshSession(user, tokenHash, familyId, expiresAt);
		// saveAndFlush (not save): RefreshSession.id is a Hibernate-generated
		// UUID, same reasoning as User/CommunityResource — see ADR-007. The
		// assigned id is needed immediately by rotate() to link the
		// old session's replaced_by_session_id.
		refreshSessionRepository.saveAndFlush(session);

		return new CreatedSession(new IssuedRefreshToken(rawToken, expiresAt), session);
	}

}
