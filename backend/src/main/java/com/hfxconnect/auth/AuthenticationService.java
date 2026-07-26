package com.hfxconnect.auth;

import com.hfxconnect.common.text.EmailNormalizer;
import com.hfxconnect.user.AccountStatus;
import com.hfxconnect.user.User;
import com.hfxconnect.user.UserRepository;
import com.hfxconnect.user.UserResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login: verifies credentials and, only on success, issues a new access
 * token and refresh session. Never distinguishes "unknown email" from "wrong
 * password" in either its response or its own timing — see
 * {@link #login(String, String)}'s dummy-hash comparison.
 */
@Service
public class AuthenticationService {

	/**
	 * Compared against with {@link PasswordEncoder#matches} whenever no
	 * account exists for the submitted email, so that "email doesn't exist"
	 * and "email exists, password is wrong" take approximately the same
	 * amount of time — BCrypt's own cost dominates either way, so skipping
	 * the comparison entirely for an unknown email would otherwise make the
	 * two cases measurably distinguishable by response time, quietly
	 * defeating the generic {@link AuthenticationFailedException} message.
	 * Computed once at startup (a real, fixed-format BCrypt hash — not a
	 * placeholder string), not per request.
	 */
	private String dummyPasswordHash;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final AccessTokenService accessTokenService;
	private final RefreshSessionService refreshSessionService;

	public AuthenticationService(
			UserRepository userRepository,
			PasswordEncoder passwordEncoder,
			AccessTokenService accessTokenService,
			RefreshSessionService refreshSessionService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.accessTokenService = accessTokenService;
		this.refreshSessionService = refreshSessionService;
	}

	@PostConstruct
	void computeDummyPasswordHash() {
		this.dummyPasswordHash = passwordEncoder.encode("timing-attack-mitigation-only-never-a-real-account");
	}

	@Transactional
	public AuthSessionResult login(String email, String password) {
		String normalizedEmail = EmailNormalizer.normalize(email);
		User user = userRepository.findByNormalizedEmail(normalizedEmail).orElse(null);

		String hashToCompareAgainst = user != null ? user.getPasswordHash() : dummyPasswordHash;
		boolean passwordMatches = passwordEncoder.matches(password, hashToCompareAgainst);

		if (user == null || !passwordMatches) {
			throw new AuthenticationFailedException();
		}
		if (user.getStatus() != AccountStatus.ACTIVE) {
			throw new AccountUnavailableException();
		}

		AccessTokenService.IssuedAccessToken accessToken = accessTokenService.issue(user.getId(), user.getRole());
		RefreshSessionService.IssuedRefreshToken refreshToken = refreshSessionService.issueNewSession(user);

		return new AuthSessionResult(
				accessToken.token(),
				accessToken.expiresInSeconds(),
				refreshToken.rawToken(),
				toUserResponse(user));
	}

	static UserResponse toUserResponse(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getRole(), user.getStatus(),
				user.isEmailVerified(), user.getCreatedAt());
	}

}
