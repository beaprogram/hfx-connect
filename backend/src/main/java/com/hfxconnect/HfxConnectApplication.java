package com.hfxconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * {@code UserDetailsServiceAutoConfiguration} is excluded because this
 * project never uses Spring Security's {@code UserDetailsService}/
 * {@code AuthenticationManager} abstractions (see ADR-009's "What This
 * Project Does Not Use") — authentication is entirely this project's own
 * Bearer-access-token filter. Without this exclusion, Spring Boot would
 * detect no {@code UserDetailsService} bean and auto-configure a default
 * one with a random generated password printed to the console on every
 * startup, which this project neither uses nor wants logged.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class HfxConnectApplication {

	public static void main(String[] args) {
		SpringApplication.run(HfxConnectApplication.class, args);
	}

}
