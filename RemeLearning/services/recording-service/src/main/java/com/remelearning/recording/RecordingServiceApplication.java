package com.remelearning.recording;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for recording-service: lesson recording ingestion (realtime + upload), S3 storage, publishes recording.uploaded. */
@SpringBootApplication
public class RecordingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(RecordingServiceApplication.class, args);
	}
}
