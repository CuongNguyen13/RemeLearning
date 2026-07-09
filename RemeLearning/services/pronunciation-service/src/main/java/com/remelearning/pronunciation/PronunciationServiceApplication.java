package com.remelearning.pronunciation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for pronunciation-service: pronunciation scoring and phoneme-level error detection. */
@SpringBootApplication
public class PronunciationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PronunciationServiceApplication.class, args);
	}
}
