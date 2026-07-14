package com.remelearning.english.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI / OpenAPI metadata for english-service. Served at /swagger-ui.html and /v3/api-docs. */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI openApi() {
		return new OpenAPI()
				.info(new Info()
						.title("English Service API")
						.description("English-skill analysis: vocabulary, grammar, pronunciation")
						.version("v1"));
	}
}
