package com.hfxconnect.resource;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceOperatingHoursRepository extends JpaRepository<ResourceOperatingHours, Long> {

	/**
	 * Unordered — callers that need Monday-Sunday order sort in memory by
	 * {@code DayOfWeek.getValue()} (see {@code OperatingHoursResponse.from}).
	 * A derived {@code OrderByDayOfWeek} query would sort by the underlying
	 * {@code VARCHAR} column's alphabetical order (FRIDAY, MONDAY, ...), not
	 * calendar order, since {@code day_of_week} is stored as a readable
	 * string (see V6's design note) — this is deliberately not relied on.
	 */
	List<ResourceOperatingHours> findByResourceId(UUID resourceId);

	/**
	 * The batch-load query behind the public resource list's N+1 avoidance —
	 * one query for every resource id on a page, grouped in memory by the
	 * caller. See ADR-011's "Batch-Loading Operating Hours" section.
	 */
	List<ResourceOperatingHours> findByResourceIdIn(Collection<UUID> resourceIds);

	void deleteByResourceId(UUID resourceId);

}
