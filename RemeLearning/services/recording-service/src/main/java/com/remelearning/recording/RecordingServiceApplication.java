package com.remelearning.recording;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Entry point for recording-service: lesson recording ingestion (realtime + upload), S3 storage,
 * publishes recording.uploaded. Scans com.remelearning broadly (not just this service's own
 * package) so shared beans from the common module - e.g. GlobalExceptionHandler - are picked up.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.remelearning")
@MapperScan("com.remelearning.recording.mapper")
public class RecordingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RecordingServiceApplication.class, args);
	}
}
