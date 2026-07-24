package com.hfxconnect.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hfxconnect.common.error.ValidationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Pure unit tests of {@link RegistrationService}'s business rules, using a
 * mocked {@link UserRepository} and {@link PasswordEncoder} so the
 * exception-translation and race-condition handling paths can be exercised
 * deterministically without a real database or real (slow) BCrypt hashing.
 * Real database behavior (constraints, persistence) is covered separately by
 * {@link UserRepositoryIntegrationTest}; real BCrypt behavior is covered by
 * {@link UserApiIntegrationTest} (full stack, real {@code PasswordEncoder} bean).
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	private RegistrationService registrationService;

	@BeforeEach
	void setUp() {
		registrationService = new RegistrationService(userRepository, passwordEncoder);
	}

	@Test
	void registerNormalizesEmailAndAssignsTheSafestDefaultRoleAndStatus() {
		when(userRepository.existsByNormalizedEmail("student@example.org")).thenReturn(false);
		when(passwordEncoder.encode("correcthorsebattery")).thenReturn("hashed-value");
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> asIfPersisted(invocation.getArgument(0)));

		UserResponse response = registrationService.register(
				new RegistrationRequest("  Student@Example.org  ", "correcthorsebattery"));

		assertThat(response.email()).isEqualTo("Student@Example.org");
		assertThat(response.role()).isEqualTo(Role.USER);
		assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
		assertThat(response.emailVerified()).isFalse();
	}

	@Test
	void registerHashesThePasswordRatherThanStoringItInPlaintext() {
		when(userRepository.existsByNormalizedEmail(anyString())).thenReturn(false);
		when(passwordEncoder.encode("correcthorsebattery")).thenReturn("$2a$12$hashedValueNotThePlaintext");
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> asIfPersisted(invocation.getArgument(0)));

		registrationService.register(new RegistrationRequest("hash-check@example.org", "correcthorsebattery"));

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$12$hashedValueNotThePlaintext");
		assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("correcthorsebattery");
		verify(passwordEncoder).encode("correcthorsebattery");
	}

	@Test
	void registerRejectsMissingEmail() {
		assertThatThrownBy(() -> registrationService.register(new RegistrationRequest(null, "correcthorsebattery")))
				.isInstanceOf(ValidationException.class);

		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void registerRejectsInvalidEmailFormat() {
		assertThatThrownBy(() -> registrationService.register(new RegistrationRequest("not-an-email", "correcthorsebattery")))
				.isInstanceOf(ValidationException.class);

		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void registerRejectsWeakPassword() {
		assertThatThrownBy(() -> registrationService.register(new RegistrationRequest("weak@example.org", "short")))
				.isInstanceOf(ValidationException.class);

		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void registerRejectsDuplicateNormalizedEmail() {
		when(userRepository.existsByNormalizedEmail("duplicate@example.org")).thenReturn(true);

		assertThatThrownBy(() -> registrationService.register(new RegistrationRequest("duplicate@example.org", "correcthorsebattery")))
				.isInstanceOf(UserConflictException.class);

		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	void registerTranslatesADatabaseRaceConditionIntoAConflict() {
		// Simulates two concurrent requests both passing the application-level
		// existsByNormalizedEmail() pre-check before either has committed, so
		// the database's unique constraint is what actually rejects the
		// second insert.
		when(userRepository.existsByNormalizedEmail(anyString())).thenReturn(false);
		when(passwordEncoder.encode(anyString())).thenReturn("hashed-value");
		when(userRepository.saveAndFlush(any(User.class)))
				.thenThrow(new DataIntegrityViolationException(
						"duplicate key value violates unique constraint \"users_normalized_email_key\""));

		assertThatThrownBy(() -> registrationService.register(new RegistrationRequest("race@example.org", "correcthorsebattery")))
				.isInstanceOf(UserConflictException.class);
	}

	@Test
	void registerCannotBeMadeToAssignAnyRoleOtherThanUser() {
		// RegistrationRequest has no role field at all, so there is no
		// input value that could reach the entity constructor as a role —
		// this test documents that invariant by construction rather than by
		// trying (and failing) to pass a role through.
		when(userRepository.existsByNormalizedEmail(anyString())).thenReturn(false);
		when(passwordEncoder.encode(anyString())).thenReturn("hashed-value");
		when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> asIfPersisted(invocation.getArgument(0)));

		UserResponse response = registrationService.register(new RegistrationRequest("no-escalation@example.org", "correcthorsebattery"));

		assertThat(response.role()).isEqualTo(Role.USER);
	}

	/** Simulates what {@code saveAndFlush()} would return after JPA assigns an ID and {@code @PrePersist} runs. */
	private static User asIfPersisted(User user) {
		return new User(UUID.randomUUID(), user.getEmail(), user.getNormalizedEmail(), user.getPasswordHash(),
				user.getRole(), user.getStatus(), user.isEmailVerified(), Instant.now(), Instant.now());
	}

}
