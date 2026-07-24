package com.hfxconnect.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * All registration business logic: input normalization/validation, password
 * hashing, duplicate-email detection, and mapping to the safe response DTO.
 * See {@code docs/architecture/backend-architecture.md} for the layering
 * pattern this follows (the same one {@code CategoryService} and
 * {@code ResourceService} established), and ADR-007 for the password-hashing
 * and account-status design decisions.
 *
 * <p>Never creates access tokens, refresh tokens, or an authenticated
 * session — registration does not log the caller in. That is Milestone 5B's
 * responsibility.
 */
@Service
public class RegistrationService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public RegistrationService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public UserResponse register(RegistrationRequest request) {
		RegistrationValidation.Normalized fields = RegistrationValidation.validate(request.email(), request.password());

		if (userRepository.existsByNormalizedEmail(fields.normalizedEmail())) {
			throw UserConflictException.duplicateEmail();
		}

		String passwordHash = passwordEncoder.encode(fields.password());
		User user = new User(fields.email(), fields.normalizedEmail(), passwordHash);

		try {
			// saveAndFlush (not save): User's id is a Hibernate-generated UUID
			// assigned in memory, unlike Category's IDENTITY column, so a
			// plain save() is not guaranteed to hit the database
			// synchronously — the INSERT could otherwise be deferred to the
			// next flush, which would happen after this try/catch has
			// already exited. Flushing explicitly is what makes the
			// race-condition catch below reliable. Mirrors
			// ResourceService.create().
			user = userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException ex) {
			// Same reasoning as CategoryService.create()/ResourceService.create():
			// the exists() check above narrows the race window but does not
			// close it; the database's unique constraint on normalized_email
			// is authoritative.
			throw UserConflictException.duplicateEmail();
		}

		return UserResponse.from(user);
	}

}
