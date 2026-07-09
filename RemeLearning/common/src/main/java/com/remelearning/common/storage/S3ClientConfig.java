package com.remelearning.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

/**
 * Builds the shared {@link S3Client} bean from {@code reme.s3.*} properties.
 * {@code endpoint} can point at an S3-compatible service (e.g. MinIO) for local development.
 */
@Configuration
@ConfigurationProperties(prefix = "reme.s3")
public class S3ClientConfig {

	private String region = "ap-southeast-1";
	private String accessKey;
	private String secretKey;
	private String endpoint;

	@Bean
	public S3Client s3Client() {
		S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
		if (accessKey != null && secretKey != null) {
			builder.credentialsProvider(StaticCredentialsProvider.create(
					AwsBasicCredentials.create(accessKey, secretKey)));
		}
		if (endpoint != null && !endpoint.isBlank()) {
			builder.endpointOverride(URI.create(endpoint));
		}
		return builder.build();
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}
}
