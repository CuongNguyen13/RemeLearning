package com.remelearning.grammar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for grammar-service: grammar error detection via LLM analysis of transcripts. */
@SpringBootApplication
public class GrammarServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(GrammarServiceApplication.class, args);
	}
}
