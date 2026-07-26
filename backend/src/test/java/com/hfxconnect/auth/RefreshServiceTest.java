package com.hfxconnect.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hfxconnect.user.Role;
import com.hfxconnect.user.User;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshServiceTest {

	@Mock
	private RefreshSessionService refreshSessionService;

	@Mock
	private AccessTokenService accessTokenService;

	private RefreshService refreshService;

	@BeforeEach
	void setUp() {
		refreshService = new RefreshService(refreshSessionService, accessTokenService);
	}

	@Test
	void missingCookieThrowsAuthenticationRequiredWithoutTouchingTheSessionService() {
		assertThatThrownBy(() -> refreshService.refresh(null)).isInstanceOf(AuthenticationRequiredException.class);
		assertThatThrownBy(() -> refreshService.refresh("")).isInstanceOf(AuthenticationRequiredException.class);
		assertThatThrownBy(() -> refreshService.refresh("   ")).isInstanceOf(AuthenticationRequiredException.class);

		verify(refreshSessionService, never()).rotate(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void aValidTokenRotatesAndReturnsANewAccessTokenAndRefreshToken() {
		User user = new User("student@example.org", "student@example.org", "hash");
		when(refreshSessionService.rotate("old-token")).thenReturn(
				new RefreshSessionService.RotatedRefreshToken("new-raw-token", Instant.now().plusSeconds(3600), user));
		when(accessTokenService.issue(user.getId(), Role.USER))
				.thenReturn(new AccessTokenService.IssuedAccessToken("new-jwt", 900));

		AuthSessionResult result = refreshService.refresh("old-token");

		assertThat(result.accessToken()).isEqualTo("new-jwt");
		assertThat(result.expiresInSeconds()).isEqualTo(900);
		assertThat(result.rawRefreshToken()).isEqualTo("new-raw-token");
		assertThat(result.user().email()).isEqualTo("student@example.org");
	}

	@Test
	void exceptionsFromRotationPropagateUnchanged() {
		when(refreshSessionService.rotate("bad-token")).thenThrow(new InvalidRefreshTokenException());

		assertThatThrownBy(() -> refreshService.refresh("bad-token")).isInstanceOf(InvalidRefreshTokenException.class);

		verify(accessTokenService, never()).issue(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

}
