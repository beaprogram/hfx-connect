package com.hfxconnect.common.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@code bearerAuth} names the {@code Authorization: Bearer <token>} scheme
 * used by every protected endpoint (Milestone 5C — see ADR-009). Referenced
 * by {@code @SecurityRequirement(name = "bearerAuth")} on each protected
 * operation, not applied globally, so public endpoints are never rendered
 * as if they required a token.
 */
@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {

	@Bean
	public OpenAPI hfxConnectOpenApi() {
		return new OpenAPI().info(new Info()
				.title("HFX Connect API")
				.description("REST API for the HFX Connect community-resource platform. Most endpoints are "
						+ "public; GET /api/v1/users/me and the category/resource POST endpoints require a "
						+ "Bearer access token — see each endpoint's own description and docs/api/README.md.")
				.version("v1"));
	}

}
