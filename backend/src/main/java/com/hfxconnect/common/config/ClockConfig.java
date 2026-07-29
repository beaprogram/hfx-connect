package com.hfxconnect.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single production {@link Clock} bean, injected everywhere "now" is
 * needed instead of calling {@code Instant.now()}/{@code LocalTime.now()}
 * directly — see ADR-011's "Clock Injected, Never Instant.now() Directly"
 * section. {@code Clock.systemUTC()} is the only place
 * {@code Clock.systemUTC()} is called in the whole codebase; every consumer
 * (e.g. {@code OpenNowCalculator}) converts through the specific zone it
 * needs (e.g. {@code America/Halifax}) rather than assuming the clock's own
 * zone. Tests never depend on this bean — they construct their own
 * {@code Clock.fixed(...)} for deterministic behavior.
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
