package com.hfxconnect.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Schema(description = "One day's schedule entry. A closed day must omit opensAt/closesAt; an open day must supply both, and they must not be equal (see ADR-011).")
public record OperatingHoursEntryRequest(

		@Schema(description = "MONDAY through SUNDAY.", example = "MONDAY")
		DayOfWeek dayOfWeek,

		@Schema(description = "True if the resource is closed all day on dayOfWeek.")
		boolean closed,

		@Schema(description = "Local time the resource opens. Required unless closed.", example = "09:00")
		LocalTime opensAt,

		@Schema(description = "Local time the resource closes. If earlier than opensAt, the interval crosses midnight (see ADR-011). Required unless closed.", example = "17:30")
		LocalTime closesAt) {

}
