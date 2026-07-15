package com.remelearning.bff.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Base URLs of the domain services this BFF composes calls to.
 * Plain host:port for now; once service discovery (e.g. Eureka) is introduced,
 * these can be swapped for logical service names.
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "reme.services")
public class DownstreamServicesProperties {

	private String user = "http://localhost:8081";
	private String recording = "http://localhost:8082";
	// vocabulary/grammar/pronunciation were merged into one english-service (port 8085); one field, not three
	private String english = "http://localhost:8085";
	private String recommendation = "http://localhost:8086";
	private String dashboard = "http://localhost:8087";
}
