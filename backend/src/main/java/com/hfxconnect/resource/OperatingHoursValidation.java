package com.hfxconnect.resource;

import com.hfxconnect.common.error.ValidationException;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates a {@link ReplaceOperatingHoursRequest} and normalizes it into a
 * {@link Normalized} list of {@link OperatingHoursEntry} — the schedule's
 * business rules (duplicate days, closed-with-times, missing times, equal
 * times, list size) live entirely here rather than as Bean Validation
 * annotations, the same layering {@code ResourceValidation} already
 * established for {@code CommunityResource}: Jackson/Bean Validation only
 * needs to prove the JSON is well-formed (handled globally by
 * {@code GlobalExceptionHandler}); everything else is a business rule this
 * class checks explicitly, accumulating every violation before throwing so
 * a caller sees the complete picture in one response.
 */
final class OperatingHoursValidation {

	static final int MAX_ENTRIES = 7;

	private OperatingHoursValidation() {
	}

	record Normalized(List<OperatingHoursEntry> entries) {
	}

	static Normalized validate(ReplaceOperatingHoursRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		List<OperatingHoursEntryRequest> hours = request == null ? null : request.hours();

		if (hours == null) {
			errors.put("hours", "hours is required.");
			throw new ValidationException("The submitted operating hours contain invalid information.", errors);
		}
		if (hours.size() > MAX_ENTRIES) {
			errors.put("hours", "hours must contain at most " + MAX_ENTRIES + " entries.");
		}

		Set<DayOfWeek> seenDays = new HashSet<>();
		List<OperatingHoursEntry> normalized = new ArrayList<>();

		for (int i = 0; i < hours.size(); i++) {
			OperatingHoursEntryRequest entry = hours.get(i);
			String key = "hours[" + i + "]";

			if (entry == null) {
				errors.put(key, "Each entry in hours must not be null.");
				continue;
			}
			if (entry.dayOfWeek() == null) {
				errors.put(key + ".dayOfWeek", "dayOfWeek is required.");
				continue;
			}

			key = "hours[" + entry.dayOfWeek() + "]";
			if (!seenDays.add(entry.dayOfWeek())) {
				errors.put(key, "dayOfWeek " + entry.dayOfWeek() + " is duplicated.");
				continue;
			}

			if (entry.closed()) {
				if (entry.opensAt() != null || entry.closesAt() != null) {
					errors.put(key, "A closed day must not include opensAt or closesAt.");
					continue;
				}
				normalized.add(new OperatingHoursEntry(entry.dayOfWeek(), true, null, null));
			} else {
				if (entry.opensAt() == null || entry.closesAt() == null) {
					errors.put(key, "An open day requires both opensAt and closesAt.");
					continue;
				}
				if (entry.opensAt().equals(entry.closesAt())) {
					errors.put(key, "opensAt and closesAt must not be equal.");
					continue;
				}
				normalized.add(new OperatingHoursEntry(entry.dayOfWeek(), false, entry.opensAt(), entry.closesAt()));
			}
		}

		if (!errors.isEmpty()) {
			throw new ValidationException("The submitted operating hours contain invalid information.", errors);
		}

		return new Normalized(normalized);
	}

}
