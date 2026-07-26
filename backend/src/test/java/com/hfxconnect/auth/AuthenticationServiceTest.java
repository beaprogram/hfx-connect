package com.hfxconnect.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hfxconnect.user.Role;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Pure unit tests of {@link AuthenticationService}'s business rules, with
 * every collaborator mocked — this is the layer that verifies the generic-
 * failure and timing-mitigation behavior deterministically, without needing
 * real BCrypt cost or a real database. Real password hashing and real
 * database behavior are covered separately by {@link AuthSessionApiIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private AccessTokenService accessTokenService;

	@Mock
	private RefreshSessionService refreshSessionService;

	private AuthenticationService authenticationService;

	@BeforeEach
	void setUp() {
		authenticationService = new AuthenticationService(userRepository, passwordEncoder, accessTokenService, refreshSessionService);
		// computeDummyPasswordHash() calls passwordEncoder.encode(...) on
		// every test — a mocked PasswordEncoder returns null by default,
		// which would make the later passwordEncoder.matches(x, null) call
		// fail Mockito's strict-stubbing argument matching (anyString()
		// never matches a null argument).
		when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$dummyHashForTimingMitigationOnly");
		authenticationService.computeDummyPasswordHash();
	}

	@Test
	void validLoginReturnsAnAccessTokenARefreshTokenAndASafeUser() {
		User user = activeUser("student@example.org");
		when(userRepository.findByNormalizedEmail("student@example.org")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("correcthorsebattery", user.getPasswordHash())).thenReturn(true);
		when(accessTokenService.issue(user.getId(), Role.USER))
				.thenReturn(new AccessTokenService.IssuedAccessToken("jwt-value", 900));
		when(refreshSessionService.issueNewSession(user))
				.thenReturn(new RefreshSessionService.IssuedRefreshToken("raw-refresh-token", Instant.now().plusSeconds(2_592_000)));

		AuthSessionResult result = authenticationService.login("student@example.org", "correcthorsebattery");

		assertThat(result.accessToken()).isEqualTo("jwt-value");
		assertThat(result.expiresInSeconds()).isEqualTo(900);
		assertThat(result.rawRefreshToken()).isEqualTo("raw-refresh-token");
		assertThat(result.user().id()).isEqualTo(user.getId());
		assertThat(result.user().email()).isEqualTo(user.getEmail());
	}

	@Test
	void loginNormalizesEmailBeforeLookup() {
		User user = activeUser("student@example.org");
		when(userRepository.findByNormalizedEmail("student@example.org")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
		when(accessTokenService.issue(any(), any())).thenReturn(new AccessTokenService.IssuedAccessToken("jwt", 900));
		when(refreshSessionService.issueNewSession(any()))
				.thenReturn(new RefreshSessionService.IssuedRefreshToken("raw", Instant.now().plusSeconds(3600)));

		authenticationService.login("  Student@Example.org  ", "correcthorsebattery");

		verify(userRepository).findByNormalizedEmail("student@example.org");
	}

	@Test
	void wrongPasswordThrowsAuthenticationFailed() {
		User user = activeUser("student@example.org");
		when(userRepository.findByNormalizedEmail("student@example.org")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-password", user.getPasswordHash())).thenReturn(false);

		assertThatThrownBy(() -> authenticationService.login("student@example.org", "wrong-password"))
				.isInstanceOf(AuthenticationFailedException.class);

		verify(refreshSessionService, never()).issueNewSession(any());
	}

	@Test
	void unknownEmailThrowsTheExactSameAuthenticationFailedExceptionAsWrongPassword() {
		when(userRepository.findByNormalizedEmail("nobody@example.org")).thenReturn(Optional.empty());
		when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

		assertThatThrownBy(() -> authenticationService.login("nobody@example.org", "some-password"))
				.isInstanceOf(AuthenticationFailedException.class)
				.hasMessage("Invalid email or password.");
	}

	@Test
	void unknownEmailStillPerformsAPasswordComparisonAgainstTheDummyHashForTimingParity() {
		when(userRepository.findByNormalizedEmail("nobody@example.org")).thenReturn(Optional.empty());
		when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

		assertThatThrownBy(() -> authenticationService.login("nobody@example.org", "some-password"))
				.isInstanceOf(AuthenticationFailedException.class);

		// The comparison happens against *some* precomputed hash, not the
		// real user's (there is none) — proving the encoder is invoked at
		// all (not skipped) is what defeats the timing side channel.
		verify(passwordEncoder, times(1)).matches(eq("some-password"), anyString());
	}

	// Inactive-account rejection (ACCOUNT_UNAVAILABLE) is covered by
	// AuthSessionApiIntegrationTest instead of here: User has no
	// production setter for status (only the registration constructor,
	// which always assigns ACTIVE — see ADR-007), and User's package-private
	// full constructor is not visible from this package, so producing a
	// non-ACTIVE User for a mocked unit test would need a test-only backdoor
	// this project's own conventions don't otherwise justify. The API test
	// sets status via a direct SQL UPDATE after registration instead, the
	// same way CategoryRepositoryIntegrationTest et al. already reach states
	// an entity's own public API doesn't produce.

	@Test
	void loginResultNeverContainsThePlaintextPassword() {
		User user = activeUser("student@example.org");
		when(userRepository.findByNormalizedEmail("student@example.org")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("correcthorsebattery", user.getPasswordHash())).thenReturn(true);
		when(accessTokenService.issue(any(), any())).thenReturn(new AccessTokenService.IssuedAccessToken("jwt", 900));
		when(refreshSessionService.issueNewSession(any()))
				.thenReturn(new RefreshSessionService.IssuedRefreshToken("raw", Instant.now().plusSeconds(3600)));

		AuthSessionResult result = authenticationService.login("student@example.org", "correcthorsebattery");

		assertThat(result.toString()).doesNotContain("correcthorsebattery");
	}

	/** {@link User}'s public constructor always assigns {@code Role.USER}/{@code AccountStatus.ACTIVE} — see ADR-007. */
	private static User activeUser(String email) {
		return new User(email, email, "$2a$12$storedHashPlaceholderPlaceholderPlace");
	}

}
