package com.remelearning.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for bff-service: the single HTTP entry point for the web/mobile client.
 * Composes calls to the domain services (user, recording, pronunciation, grammar,
 * vocabulary, recommendation, dashboard) and shapes the combined response for the UI,
 * instead of the frontend having to call each service directly.
 */
@SpringBootApplication
public class BffServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BffServiceApplication.class, args);
	}
}
