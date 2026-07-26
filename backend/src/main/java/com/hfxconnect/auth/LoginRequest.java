package com.hfxconnect.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * HTTP request body for login. Deliberately as thin as
 * {@code com.hfxconnect.user.RegistrationRequest} — see that record's
 * Javadoc for why {@code @JsonIgnoreProperties(ignoreUnknown = true)} is used
 * here too and why stricter rejection isn't, for this project's specific
 * Jackson 3.x/Spring Boot 4.1 stack.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Credentials for login. Email is normalized (trimmed, lowercased) identically to registration before lookup.")
public record LoginRequest(

		@Schema(example = "student@example.org")
		@NotBlank(message = "Email is required.")
		String email,

		@Schema(example = "correct-horse-battery")
		@NotBlank(message = "Password is required.")
		String password) {

}
