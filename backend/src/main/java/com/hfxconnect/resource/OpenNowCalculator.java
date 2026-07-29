package com.hfxconnect.resource;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Calculates a resource's current open status from its weekly schedule. See
 * ADR-011 for the full timezone, overnight-interval, and status-model
 * reasoning this class implements.
 *
 * <p>Takes a {@link Clock} constructor dependency rather than calling
 * {@code Instant.now()} directly, so every calculation is deterministic
 * given a fixed clock in tests, and always converts through
 * {@link #HALIFAX_ZONE} regardless of the clock's own zone (the production
 * bean, {@code ClockConfig}, is UTC).
 *
 * <p>{@link #nowInHalifax()} and {@link #calculate(List, ZonedDateTime)} are
 * deliberately separate: {@code ResourceService} computes "now" exactly
 * once per request/page and reuses it for every resource on that page (via
 * both the repository's {@code openNow} filter and this calculator), rather
 * than each resource independently reading the clock — see ADR-011's
 * "Batch-Loading" section.
 */
@Service
public class OpenNowCalculator {

	static final ZoneId HALIFAX_ZONE = ZoneId.of("America/Halifax");

	private final Clock clock;

	public OpenNowCalculator(Clock clock) {
		this.clock = clock;
	}

	public record Result(HoursStatus status, Boolean openNow) {
	}

	public ZonedDateTime nowInHalifax() {
		return ZonedDateTime.now(clock).withZoneSameInstant(HALIFAX_ZONE);
	}

	/**
	 * A resource with no schedule rows at all is {@link HoursStatus#UNKNOWN}
	 * (openNow {@code null}) — never a guess. Otherwise resolves to
	 * {@link HoursStatus#OPEN} or {@link HoursStatus#CLOSED}, evaluating
	 * every entry's same-day and overnight-continuation intervals against
	 * {@code nowHalifax}.
	 */
	public Result calculate(List<OperatingHoursEntry> weeklyHours, ZonedDateTime nowHalifax) {
		if (weeklyHours.isEmpty()) {
			return new Result(HoursStatus.UNKNOWN, null);
		}

		DayOfWeek today = nowHalifax.getDayOfWeek();
		DayOfWeek yesterday = today.minus(1);
		LocalTime now = nowHalifax.toLocalTime();

		boolean open = weeklyHours.stream().anyMatch(entry -> isOpenAt(entry, today, yesterday, now));
		return new Result(open ? HoursStatus.OPEN : HoursStatus.CLOSED, open);
	}

	private static boolean isOpenAt(OperatingHoursEntry entry, DayOfWeek today, DayOfWeek yesterday, LocalTime now) {
		if (entry.closed()) {
			return false;
		}
		if (entry.dayOfWeek() == today) {
			return entry.overnight()
					? !now.isBefore(entry.opensAt())
					: !now.isBefore(entry.opensAt()) && now.isBefore(entry.closesAt());
		}
		if (entry.dayOfWeek() == yesterday && entry.overnight()) {
			return now.isBefore(entry.closesAt());
		}
		return false;
	}

}
