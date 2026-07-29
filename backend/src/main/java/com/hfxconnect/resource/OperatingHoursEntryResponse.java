package com.hfxconnect.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Schema(description = "One day's resolved schedule entry.")
public record OperatingHoursEntryResponse(
		DayOfWeek dayOfWeek,
		boolean closed,
		LocalTime opensAt,
		LocalTime closesAt,
		@Schema(description = "True if this interval crosses midnight (opensAt is later in the day than closesAt) — see ADR-011.")
		boolean overnight) {

	static OperatingHoursEntryResponse from(OperatingHoursEntry entry) {
		return new OperatingHoursEntryResponse(
				entry.dayOfWeek(), entry.closed(), entry.opensAt(), entry.closesAt(), entry.overnight());
	}

}
