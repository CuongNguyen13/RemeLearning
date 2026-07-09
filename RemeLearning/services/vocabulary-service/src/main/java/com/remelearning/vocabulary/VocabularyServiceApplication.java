package com.remelearning.vocabulary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for vocabulary-service: vocabulary usage analysis and suggestion. */
@SpringBootApplication
public class VocabularyServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(VocabularyServiceApplication.class, args);
	}
}
