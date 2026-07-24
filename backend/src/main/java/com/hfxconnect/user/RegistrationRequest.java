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
 * {@code role}, {@code status}, {@code emailVerified}, {@code passwordHash},
 * or other privilege-bearing field, and submitting one (e.g.
 * {@code {"role":"ADMIN", ...}}) is silently ignored rather than failing
 * deserialization — either way, no such field exists for a value to bind to,
 * so privilege escalation through the registration request body is not
 * possible. {@link RegistrationService} always assigns {@link Role#USER}.
 *
 * <p><strong>Rejecting unknown fields with {@code 400} instead of silently
 * ignoring them was investigated and rejected as the current behavior for
 * this project's specific stack.</strong> A class-level
 * {@code @JsonIgnoreProperties(ignoreUnknown = false)} was tried first (the
 * standard Jackson mechanism for exactly this) and verified empirically,
 * with a running instance and real {@code curl} requests, to have no effect
 * here: Spring Boot 4.1's Jackson auto-configuration globally disables
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} (Spring Boot's long-standing default,
 * confirmed present in {@code JacksonAutoConfiguration}), and for this
 * project's Jackson 3.x ({@code tools.jackson}) record-based deserialization
 * path, that global default was not overridden by the per-class annotation —
 * unlike Jackson 2's classic bean deserialization, where the same annotation
 * reliably takes precedence over the global feature. Forcing rejection would
 * require flipping {@code spring.jackson.deserialization.fail-on-unknown-properties}
 * globally, which would also change how every other endpoint's request body
 * (Category, Resource) parses unrecognized fields — a change with a blast
 * radius well beyond this endpoint, and out of scope for a focused fix here.
 * The chosen, current, and tested behavior is: privilege-bearing fields are
 * silently ignored, and privilege escalation remains impossible by
 * construction regardless.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Fields required to register an account. Only USER accounts can be created through this endpoint — any extra field (e.g. role, status, emailVerified, passwordHash) is ignored, not honored.")
public record RegistrationRequest(

		@Schema(example = "student@example.org")
		@NotBlank(message = "Email is required.")
		String email,

		@Schema(example = "correct-horse-battery")
		@NotBlank(message = "Password is required.")
		String password) {

}
