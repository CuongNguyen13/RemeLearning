package com.remelearning.dashboard.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI / OpenAPI metadata for dashboard-service. Served at /swagger-ui.html and /v3/api-docs. */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Dashboard Service API")
						.description("Learning progress dashboard and reporting")
						.version("v1"));
	}
}
