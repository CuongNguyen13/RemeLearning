package com.remelearning.bff.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI / OpenAPI metadata for bff-service. Served at /swagger-ui.html and /v3/api-docs. */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info()
						.title("BFF Service API")
						.description("Single entry point for the web/mobile client, composes calls to the domain services")
						.version("v1"));
	}
}
