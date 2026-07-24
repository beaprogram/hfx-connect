package com.hfxconnect.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * The safe, public account representation returned after registration. Never
 * includes {@code passwordHash}, {@code normalizedEmail} (an internal
 * uniqueness-checking detail, not public data — mirrors
 * {@code CategoryResponse}'s omission of {@code normalizedName}), or any
 * other internal security metadata.
 */
@Schema(description = "A registered account, as returned by the API. Never includes the password or its hash.")
public record UserResponse(
		UUID id,
		@Schema(example = "student@example.org") String email,
		Role role,
		AccountStatus status,
		boolean emailVerified,
		Instant createdAt) {

	static UserResponse from(User user) {
		return new UserResponse(
				user.getId(),
				user.getEmail(),
				user.getRole(),
				user.getStatus(),
				user.isEmailVerified(),
				user.getCreatedAt());
	}

}
