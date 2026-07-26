package com.hfxconnect.auth;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

	@Mock
	private RefreshSessionService refreshSessionService;

	@Test
	void logoutDelegatesToRefreshSessionServiceRevocation() {
		LogoutService logoutService = new LogoutService(refreshSessionService);

		logoutService.logout("some-raw-token");

		verify(refreshSessionService).revokeByRawToken("some-raw-token");
	}

	@Test
	void logoutWithNoCookieStillDelegatesSafely() {
		LogoutService logoutService = new LogoutService(refreshSessionService);

		logoutService.logout(null);

		verify(refreshSessionService).revokeByRawToken(null);
	}

	@Test
	void logoutNeverThrowsRegardlessOfInput() {
		LogoutService logoutService = new LogoutService(refreshSessionService);

		logoutService.logout("anything");
		logoutService.logout(null);
		logoutService.logout("");
	}

}
