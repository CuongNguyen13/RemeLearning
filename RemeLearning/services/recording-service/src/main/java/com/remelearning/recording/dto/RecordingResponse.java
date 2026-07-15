package com.remelearning.recording.dto;

import java.time.Instant;

public record RecordingResponse(
		String recordingId,
		String userId,
		String status,
		String s3Bucket,
		String s3Key,
		Instant createdAt) {
}
