package com.remelearning.vocabulary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Entry point for vocabulary-service: vocabulary usage analysis and suggestion.
 * Scans com.remelearning broadly (not just this service's own package) so shared beans
 * from the common module - e.g. GlobalExceptionHandler - are picked up.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.remelearning")
public class VocabularyServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(VocabularyServiceApplication.class, args);
	}
}
