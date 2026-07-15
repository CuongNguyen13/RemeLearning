package com.remelearning.recommendation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Entry point for recommendation-service: aggregates weaknesses across domains and generates
 * personalized exercises. Scans com.remelearning broadly (not just this service's own package)
 * so shared beans from the common module - e.g. GlobalExceptionHandler, KafkaEventPublisher -
 * are picked up.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.remelearning")
@MapperScan("com.remelearning.recommendation.mapper")
public class RecommendationServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RecommendationServiceApplication.class, args);
	}
}
