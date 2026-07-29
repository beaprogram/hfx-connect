package com.hfxconnect.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hfxconnect.common.error.ValidationException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class OperatingHoursValidationTest {

	@Test
	void acceptsAValidOpenDay() {
		OperatingHoursValidation.Normalized result = OperatingHoursValidation.validate(
				request(entry(DayOfWeek.MONDAY, false, "09:00", "17:00")));

		assertThat(result.entries()).hasSize(1);
		assertThat(result.entries().get(0).dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(result.entries().get(0).closed()).isFalse();
	}

	@Test
	void acceptsAValidClosedDay() {
		OperatingHoursValidation.Normalized result = OperatingHoursValidation.validate(
				request(entry(DayOfWeek.SUNDAY, true, null, null)));

		assertThat(result.entries()).hasSize(1);
		assertThat(result.entries().get(0).closed()).isTrue();
	}

	@Test
	void acceptsFewerThanSevenEntries() {
		OperatingHoursValidation.Normalized result = OperatingHoursValidation.validate(
				request(entry(DayOfWeek.MONDAY, false, "09:00", "17:00")));

		assertThat(result.entries()).hasSize(1);
	}

	@Test
	void acceptsAnOvernightInterval() {
		OperatingHoursValidation.Normalized result = OperatingHoursValidation.validate(
				request(entry(DayOfWeek.FRIDAY, false, "22:00", "02:00")));

		assertThat(result.entries().get(0).opensAt()).isEqualTo(LocalTime.of(22, 0));
		assertThat(result.entries().get(0).closesAt()).isEqualTo(LocalTime.of(2, 0));
	}

	@Test
	void rejectsANullHoursList() {
		assertValidationError(() -> OperatingHoursValidation.validate(new ReplaceOperatingHoursRequest(null)));
	}

	@Test
	void rejectsMissingDayOfWeek() {
		assertValidationError(() -> OperatingHoursValidation.validate(
				request(new OperatingHoursEntryRequest(null, false, LocalTime.of(9, 0), LocalTime.of(17, 0)))));
	}

	@Test
	void rejectsADuplicateDay() {
		assertValidationError(() -> OperatingHoursValidation.validate(new ReplaceOperatingHoursRequest(List.of(
				entry(DayOfWeek.MONDAY, false, "09:00", "17:00"),
				entry(DayOfWeek.MONDAY, false, "10:00", "18:00")))));
	}

	@Test
	void rejectsAClosedDayWithTimes() {
		assertValidationError(() -> OperatingHoursValidation.validate(
				request(new OperatingHoursEntryRequest(DayOfWeek.MONDAY, true, LocalTime.of(9, 0), null))));
	}

	@Test
	void rejectsAnOpenDayMissingOpensAt() {
		assertValidationError(() -> OperatingHoursValidation.validate(
				request(new OperatingHoursEntryRequest(DayOfWeek.MONDAY, false, null, LocalTime.of(17, 0)))));
	}

	@Test
	void rejectsAnOpenDayMissingClosesAt() {
		assertValidationError(() -> OperatingHoursValidation.validate(
				request(new OperatingHoursEntryRequest(DayOfWeek.MONDAY, false, LocalTime.of(9, 0), null))));
	}

	@Test
	void rejectsEqualOpensAtAndClosesAt() {
		assertValidationError(() -> OperatingHoursValidation.validate(
				request(entry(DayOfWeek.MONDAY, false, "09:00", "09:00"))));
	}

	@Test
	void rejectsMoreThanSevenEntries() {
		assertValidationError(() -> OperatingHoursValidation.validate(new ReplaceOperatingHoursRequest(List.of(
				entry(DayOfWeek.MONDAY, false, "09:00", "17:00"),
				entry(DayOfWeek.TUESDAY, false, "09:00", "17:00"),
				entry(DayOfWeek.WEDNESDAY, false, "09:00", "17:00"),
				entry(DayOfWeek.THURSDAY, false, "09:00", "17:00"),
				entry(DayOfWeek.FRIDAY, false, "09:00", "17:00"),
				entry(DayOfWeek.SATURDAY, false, "09:00", "17:00"),
				entry(DayOfWeek.SUNDAY, false, "09:00", "17:00"),
				entry(DayOfWeek.MONDAY, false, "09:00", "17:00")))));
	}

	@Test
	void rejectsANullListElement() {
		List<OperatingHoursEntryRequest> hours = new java.util.ArrayList<>();
		hours.add(null);
		assertValidationError(() -> OperatingHoursValidation.validate(new ReplaceOperatingHoursRequest(hours)));
	}

	private static void assertValidationError(ThrowingCallable callable) {
		assertThatThrownBy(callable).isInstanceOf(ValidationException.class);
	}

	private static ReplaceOperatingHoursRequest request(OperatingHoursEntryRequest... entries) {
		return new ReplaceOperatingHoursRequest(List.of(entries));
	}

	private static OperatingHoursEntryRequest entry(DayOfWeek dayOfWeek, boolean closed, String opensAt, String closesAt) {
		return new OperatingHoursEntryRequest(dayOfWeek, closed,
				opensAt == null ? null : LocalTime.parse(opensAt), closesAt == null ? null : LocalTime.parse(closesAt));
	}

}
