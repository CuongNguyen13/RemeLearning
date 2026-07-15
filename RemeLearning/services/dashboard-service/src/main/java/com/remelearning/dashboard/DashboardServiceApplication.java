package com.remelearning.dashboard;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Entry point for dashboard-service: a cross-domain read model for a learner's progress, built
 * purely from Kafka events (learning.gap.analyzed, recommendation.generated) - no REST calls to
 * other services. Scans com.remelearning broadly (not just this service's own package) so shared
 * beans from the common module - e.g. GlobalExceptionHandler - are picked up.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.remelearning")
@MapperScan("com.remelearning.dashboard.mapper")
public class DashboardServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DashboardServiceApplication.class, args);
	}
}
