package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

/** A learner's recording metadata, as returned by recording-service's RecordingResponse. */
@Data
public class RecordingDto {

	private String recordingId;
	private String userId;
	private String status;
	private String s3Bucket;
	private String s3Key;
	private Instant createdAt;
}
