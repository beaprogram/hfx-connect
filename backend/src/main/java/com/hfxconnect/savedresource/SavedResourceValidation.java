package com.hfxconnect.savedresource;

import com.hfxconnect.common.error.ValidationException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates and normalizes a {@link SavedResourceStatusRequest} — the same
 * "business rules live here, accumulate every violation before throwing"
 * layering {@code OperatingHoursValidation} already established.
 */
final class SavedResourceValidation {

	/**
	 * A generous but bounded ceiling on one status request — comfortably
	 * covers a full page of resource cards (Milestone 6's page size is 12;
	 * this milestone's dashboard/list pages are similarly small) while
	 * keeping the batch query's {@code IN (...)} list from growing
	 * unbounded.
	 */
	static final int MAX_STATUS_IDS = 100;

	private SavedResourceValidation() {
	}

	record Normalized(List<UUID> resourceIds) {
	}

	static Normalized validateStatusRequest(SavedResourceStatusRequest request) {
		Map<String, String> errors = new LinkedHashMap<>();
		List<UUID> resourceIds = request == null ? null : request.resourceIds();

		if (resourceIds == null) {
			errors.put("resourceIds", "resourceIds is required.");
			throw new ValidationException("The submitted request contains invalid information.", errors);
		}

		// Not `resourceIds.contains(null)`: an immutable List.of(...) (as a
		// Jackson-deserialized JSON array, or a hand-built test fixture, may
		// well be) throws NullPointerException from contains(null) itself,
		// rather than returning false — it explicitly disallows null lookups.
		boolean hasNullEntry = resourceIds.stream().anyMatch(id -> id == null);
		if (hasNullEntry) {
			errors.put("resourceIds", "resourceIds must not contain a null value.");
			throw new ValidationException("The submitted request contains invalid information.", errors);
		}

		// LinkedHashSet: normalizes duplicates while preserving first-seen order.
		Set<UUID> normalized = new LinkedHashSet<>(resourceIds);

		if (normalized.size() > MAX_STATUS_IDS) {
			errors.put("resourceIds", "resourceIds must contain at most " + MAX_STATUS_IDS + " distinct ids.");
			throw new ValidationException("The submitted request contains invalid information.", errors);
		}

		return new Normalized(List.copyOf(normalized));
	}

}
