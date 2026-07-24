package com.hfxconnect.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * A single {@link PasswordEncoder} bean, not the {@code spring-boot-starter-security}
 * dependency's auto-configured security filter chain — see ADR-007. Strength
 * 12 (not the default 10) is a deliberate, benchmarked trade-off between
 * hashing cost and registration/login latency; see the ADR for the measured
 * numbers.
 */
@Configuration
public class PasswordEncoderConfig {

	private static final int BCRYPT_STRENGTH = 12;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
	}

}
