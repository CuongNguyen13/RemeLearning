package com.remelearning.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Entry point for bff-service: the single HTTP entry point for the web/mobile client.
 * Composes calls to the domain services (user, recording, english [vocabulary/grammar/
 * pronunciation], recommendation, dashboard) and shapes the combined response for the UI,
 * instead of the frontend having to call each service directly.
 * Scans com.remelearning broadly (not just this service's own package) so shared beans
 * from the common module - e.g. GlobalExceptionHandler - are picked up.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.remelearning")
public class BffServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BffServiceApplication.class, args);
	}
}
