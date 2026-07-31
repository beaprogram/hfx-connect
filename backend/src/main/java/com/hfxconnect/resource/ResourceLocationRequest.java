package com.hfxconnect.resource;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A resource's geographic coordinate. Both fields are required. Latitude must be between -90 and 90; longitude must be between -180 and 180. See ADR-012.")
public record ResourceLocationRequest(

		@Schema(example = "44.6488")
		Double latitude,

		@Schema(example = "-63.5752")
		Double longitude) {

}
