package com.hfxconnect.resource;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Business-layer read model for a single day's operating hours — the
 * resource-id-free counterpart of {@link ResourceOperatingHours}, the same
 * split {@link ResourceDetails} already applies to {@link CommunityResource}.
 */
public record OperatingHoursEntry(DayOfWeek dayOfWeek, boolean closed, LocalTime opensAt, LocalTime closesAt) {

	static OperatingHoursEntry from(ResourceOperatingHours entity) {
		return new OperatingHoursEntry(entity.getDayOfWeek(), entity.isClosed(), entity.getOpensAt(),
				entity.getClosesAt());
	}

	/** An interval where {@code opensAt} is later than {@code closesAt} crosses midnight — see ADR-011. */
	boolean overnight() {
		return !closed && opensAt.isAfter(closesAt);
	}

}
