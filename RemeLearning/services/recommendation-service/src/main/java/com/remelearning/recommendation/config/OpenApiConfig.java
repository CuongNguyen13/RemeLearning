package com.remelearning.recommendation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI / OpenAPI metadata for recommendation-service. Served at /swagger-ui.html and /v3/api-docs. */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info()
						.title("Recommendation Service API")
						.description("Aggregates weaknesses across domains and generates personalized exercises")
						.version("v1"));
	}
}
