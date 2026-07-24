package com.hfxconnect.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * HTTP request body for registration. Bean Validation here catches the
 * simplest, order-independent violations (missing fields) declaratively;
 * email format, length, and password policy are validated by
 * {@link RegistrationValidation} in the service layer instead — see
 * {@code docs/architecture/backend-architecture.md}.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)}: this record has no
 * {@code role} (or any other privilege) field, and this annotation guarantees
 * that submitting one (e.g. {@code {"role":"ADMIN", ...}}) is silently
 * ignored rather than failing deserialization — either way, no such field
 * exists for a value to bind to, so privilege escalation through the
 * registration request body is not possible. {@link RegistrationService}
 * always assigns {@link Role#USER}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Fields required to register an account. Only USER accounts can be created through this endpoint — any extra fields (e.g. a role) are ignored, not honored.")
public record RegistrationRequest(

		@Schema(example = "student@example.org")
		@NotBlank(message = "Email is required.")
		String email,

		@Schema(example = "correct-horse-battery")
		@NotBlank(message = "Password is required.")
		String password) {

}
