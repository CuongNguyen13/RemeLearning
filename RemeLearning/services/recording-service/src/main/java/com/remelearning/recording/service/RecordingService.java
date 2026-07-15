package com.remelearning.recording.service;

import com.remelearning.recording.dto.RecordingResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Stores an uploaded recording (S3 + metadata) and publishes {@code recording.uploaded}.
 * Callers (controller) depend on this interface, not {@code RecordingServiceImpl}, so the
 * persistence/implementation strategy can change later without touching them.
 */
public interface RecordingService {

	RecordingResponse upload(MultipartFile file, String userId, String languageCode);

	RecordingResponse getByRecordingId(String recordingId);

	List<RecordingResponse> getByUserId(String userId);
}
