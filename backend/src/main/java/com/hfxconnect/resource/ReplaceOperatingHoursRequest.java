package com.hfxconnect.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Replaces a resource's entire weekly schedule. At most one entry per day (no duplicates), at most 7 entries total. A day with no entry has no schedule for that day (hours unavailable), not an implicit closed day.")
public record ReplaceOperatingHoursRequest(List<OperatingHoursEntryRequest> hours) {

}
