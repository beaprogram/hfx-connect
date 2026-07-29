package com.hfxconnect.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Comparator;
import java.util.List;

@Schema(description = "A resource's weekly schedule and currently-calculated status, always evaluated in the America/Halifax timezone (see ADR-011). hoursStatus is UNKNOWN (openNow null) only when the resource has no schedule at all; otherwise it is always OPEN or CLOSED, even for a day with no entry.")
public record OperatingHoursResponse(
		@Schema(example = "America/Halifax") String timezone,
		@Schema(description = "Ordered Monday through Sunday. A day with no entry has no schedule for that day (hours unavailable).")
		List<OperatingHoursEntryResponse> weeklyHours,
		HoursStatus hoursStatus,
		@Schema(description = "True/false when hoursStatus is OPEN/CLOSED; null when hoursStatus is UNKNOWN.")
		Boolean openNow) {

	static OperatingHoursResponse from(List<OperatingHoursEntry> weeklyHours, HoursStatus hoursStatus, Boolean openNow) {
		List<OperatingHoursEntryResponse> sorted = weeklyHours.stream()
				.sorted(Comparator.comparingInt(entry -> entry.dayOfWeek().getValue()))
				.map(OperatingHoursEntryResponse::from)
				.toList();
		return new OperatingHoursResponse("America/Halifax", sorted, hoursStatus, openNow);
	}

}
