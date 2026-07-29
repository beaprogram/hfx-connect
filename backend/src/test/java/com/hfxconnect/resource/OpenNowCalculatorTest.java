package com.hfxconnect.resource;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Every case here uses a fixed {@link Clock} — never real "now" — so the
 * suite is fully deterministic. See ADR-011 for the same-day/overnight/
 * overnight-continuation algorithm under test.
 */
class OpenNowCalculatorTest {

	@Test
	void emptyScheduleIsUnknownWithNullOpenNow() {
		OpenNowCalculator calculator = calculatorAt("2026-01-05T14:00:00Z");

		OpenNowCalculator.Result result = calculator.calculate(List.of(), calculator.nowInHalifax());

		assertThat(result.status()).isEqualTo(HoursStatus.UNKNOWN);
		assertThat(result.openNow()).isNull();
	}

	@Test
	void ordinaryIntervalIsOpenInTheMiddle() {
		// Monday, 2026-01-05 10:00 Halifax (standard time, AST = UTC-4).
		OpenNowCalculator calculator = calculatorAt("2026-01-05T14:00:00Z");
		List<OperatingHoursEntry> hours = List.of(entry(DayOfWeek.MONDAY, "09:00", "17:00"));

		OpenNowCalculator.Result result = calculator.calculate(hours, calculator.nowInHalifax());

		assertThat(result.status()).isEqualTo(HoursStatus.OPEN);
		assertThat(result.openNow()).isTrue();
	}

	@Test
	void beforeOpeningIsClosed() {
		// Monday 08:00 Halifax, entry opens at 09:00.
		OpenNowCalculator calculator = calculatorAt("2026-01-05T12:00:00Z");
		List<OperatingHoursEntry> hours = List.of(entry(DayOfWeek.MONDAY, "09:00", "17:00"));

		OpenNowCalculator.Result result = calculator.calculate(hours, calculator.nowInHalifax());

		assertThat(result.status()).isEqualTo(HoursStatus.CLOSED);
		assertThat(result.openNow()).isFalse();
	}

	@Test
	void afterClosingIsClosed() {
		// Monday 18:00 Halifax, entry closes at 17:00.
		OpenNowCalculator calculator = calculatorAt("2026-01-05T22:00:00Z");
		List<OperatingHoursEntry> hours = List.of(entry(DayOfWeek.MONDAY, "09:00", "17:00"));

		OpenNowCalculator.Result result = calculator.calculate(hours, calculator.nowInHalifax());

		assertThat(result.status()).isEqualTo(HoursStatus.CLOSED);
	}

	@Test
	void exactlyAtOpeningIsOpen() {
		// Monday 09:00:00 Halifax exactly.
		OpenNowCalculator calculator = calculatorAt("2026-01-05T13:00:00Z");
		List<OperatingHoursEntry> hours = List.of(entry(DayOfWeek.MONDAY, "09:00", "17:00"));

		OpenNowCalculator.Result result = calculator.calculate(hours, calculator.nowInHalifax());

		assertThat(result.status()).isEqualTo(HoursStatus.OPEN);
	}

	@Test
	void exactlyAtClosingIsClosed() {
		// Monday 17:00:00 Halifax exactly — the interval is [open, close).
		OpenNowCalculator calculator = calculatorAt("2026-01-05T21:00:00Z");
		List<OperatingHoursEntry> hours = List.of(entry(DayOfWeek.MONDAY, "09:00", "17:00"));

		OpenNowCalculator.Result result = calculator.calculate(hours, calculator.nowInHalifax());

		assertThat(result.status()).isEqualTo(HoursStatus.CLOSED);
	}

	@Test
	void explicitlyClosedDayIsClosedNotUnknown() {
		// Monday, explicitly closed all day.
		OpenNowCalculator calculator = calculatorAt("2026-01-05T14:00:00Z");
		List<OperatingHoursEntry> hours = List.of(new OperatingHoursEntry(DayOfWeek.MONDAY, true, null, null));

		OpenNowCalculator.Result result = calculator.calculate(hours, calculator.nowInHalifax());

		assertThat(result.status()).isEqualTo(HoursStatus.CLOSED);
		assertThat(result.openNow()).isFalse();
	}

	@Test
	void missingTodayWithNoYesterdayOvernightIsClosedNotUnknown() {
		// Monday, but the only schedule entry is for Wednesday — see ADR-011's
		// "Status Model": a non-empty schedule always resolves OPEN/CLOSED.
		OpenNowCalculator calculator = calculatorAt("2026-01-05T14:00:00Z");
		List<OperatingHoursEntry> hours = List.of(entry(DayOfWeek.WEDNESDAY, "09:00", "17:00"));

		OpenNowCalculator.Result result = calculator.calculate(hours, calculator.nowInHalifax());

		assertThat(result.status()).isEqualTo(HoursStatus.CLOSED);
		assertThat(result.openNow()).isFalse();
	}

	@Test
	void overnightIntervalIsOpenBeforeMidnightOnTheStartingDay() {
		// Monday 23:00 Halifax, entry is Monday 22:00-02:00 (crosses midnight).
		OpenNowCalculator calculator = calculatorAt("2026-01-06T03:00:00Z");
		List<OperatingHoursEntry> hours = List.of(entry(DayOfWeek.MONDAY, "22:00", "02:00"));

		OpenNowCalculator.Result result = calculator.calculate(hours, calculator.nowInHalifax());

		assertThat(result.status()).isEqualTo(HoursStatus.OPEN);
	}

	@Test
	void overnightIntervalIsOpenAfterMidnightViaThePreviousDaysEntry() {
		// Tuesday 01:00 Halifax, entry is Monday 22:00-02:00 (continues past midnight into Tuesday).
		OpenNowCalculator calculator = calculatorAt("2026-01-06T05:00:00Z");
		List<OperatingHoursEntry> hours = List.of(entry(DayOfWeek.MONDAY, "22:00", "02:00"));

		OpenNowCalculator.Result result = calculator.calculate(hours, calculator.nowInHalifax());

		assertThat(result.status()).isEqualTo(HoursStatus.OPEN);
	}

	@Test
	void overnightIntervalIsClosedAfterItsNextDayClosingTime() {
		// Tuesday 03:00 Halifax, entry is Monday 22:00-02:00 — already closed by 03:00.
		OpenNowCalculator calculator = calculatorAt("2026-01-06T07:00:00Z");
		List<OperatingHoursEntry> hours = List.of(entry(DayOfWeek.MONDAY, "22:00", "02:00"));

		OpenNowCalculator.Result result = calculator.calculate(hours, calculator.nowInHalifax());

		assertThat(result.status()).isEqualTo(HoursStatus.CLOSED);
	}

	@Test
	void sundayToMondayOvernightWrapsTheWeekCorrectly() {
		// Monday 01:00 Halifax, entry is Sunday 22:00-02:00 (continues into Monday).
		OpenNowCalculator calculator = calculatorAt("2026-01-05T05:00:00Z");
		List<OperatingHoursEntry> hours = List.of(entry(DayOfWeek.SUNDAY, "22:00", "02:00"));

		OpenNowCalculator.Result result = calculator.calculate(hours, calculator.nowInHalifax());

		assertThat(result.status()).isEqualTo(HoursStatus.OPEN);
	}

	@Test
	void halifaxConversionAppliesInStandardTime() {
		// 2026-01-05T14:00:00Z is 10:00 local in January (AST, UTC-4).
		OpenNowCalculator calculator = calculatorAt("2026-01-05T14:00:00Z");
		List<OperatingHoursEntry> hours = List.of(entry(DayOfWeek.MONDAY, "09:00", "17:00"));

		ZonedDateTime nowHalifax = calculator.nowInHalifax();

		assertThat(nowHalifax.toLocalTime()).isEqualTo(LocalTime.of(10, 0));
		assertThat(calculator.calculate(hours, nowHalifax).status()).isEqualTo(HoursStatus.OPEN);
	}

	@Test
	void halifaxConversionAppliesInDaylightTime() {
		// 2026-07-06T13:00:00Z is 10:00 local in July (ADT, UTC-3) — the same
		// wall-clock reading as the standard-time case above from a different
		// UTC instant, proving the DST offset is actually applied.
		OpenNowCalculator calculator = calculatorAt("2026-07-06T13:00:00Z");
		List<OperatingHoursEntry> hours = List.of(entry(DayOfWeek.MONDAY, "09:00", "17:00"));

		ZonedDateTime nowHalifax = calculator.nowInHalifax();

		assertThat(nowHalifax.toLocalTime()).isEqualTo(LocalTime.of(10, 0));
		assertThat(calculator.calculate(hours, nowHalifax).status()).isEqualTo(HoursStatus.OPEN);
	}

	private static OpenNowCalculator calculatorAt(String isoInstant) {
		Clock clock = Clock.fixed(Instant.parse(isoInstant), ZoneId.of("UTC"));
		return new OpenNowCalculator(clock);
	}

	private static OperatingHoursEntry entry(DayOfWeek dayOfWeek, String opensAt, String closesAt) {
		return new OperatingHoursEntry(dayOfWeek, false, LocalTime.parse(opensAt), LocalTime.parse(closesAt));
	}

}
