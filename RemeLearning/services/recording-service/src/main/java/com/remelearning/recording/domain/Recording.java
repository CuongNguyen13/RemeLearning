package com.remelearning.recording.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Persisted metadata for one uploaded recording, once its file has landed in S3. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recording {
	private Long id;
	private String recordingId;
	private String userId;
	private String s3Bucket;
	private String s3Key;
	private String languageCode;
	private String originalFilename;
	private String contentType;
	private String status;
	private Instant createdAt;
}
