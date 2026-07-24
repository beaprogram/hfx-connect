package com.hfxconnect.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI hfxConnectOpenApi() {
		return new OpenAPI().info(new Info()
				.title("HFX Connect API")
				.description("REST API for the HFX Connect community-resource platform. "
						+ "Endpoints not yet covered by authentication (Milestone 5C) are called out explicitly in their own description.")
				.version("v1"));
	}

}
