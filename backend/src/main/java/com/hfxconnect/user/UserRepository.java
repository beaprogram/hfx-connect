package com.hfxconnect.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

	boolean existsByNormalizedEmail(String normalizedEmail);

	Optional<User> findByNormalizedEmail(String normalizedEmail);

}
