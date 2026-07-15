package com.remelearning.english;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Entry point for english-service: modular monolith covering the English-skill analysis
 * domains (vocabulary, grammar, pronunciation). Each domain lives in its own package
 * (com.remelearning.english.<domain>) with its own controller/service/mapper/domain classes.
 * Scans com.remelearning broadly (not just this service's own package) so shared beans
 * from the common module - e.g. GlobalExceptionHandler - are picked up.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.remelearning")
@MapperScan({
		"com.remelearning.english.vocabulary.mapper",
		"com.remelearning.english.grammar.mapper",
		"com.remelearning.english.pronunciation.mapper",
		"com.remelearning.english.practice.mapper"
})
public class EnglishServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(EnglishServiceApplication.class, args);
	}
}
