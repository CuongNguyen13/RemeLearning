package com.remelearning.recording.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.recording.dto.RecordingResponse;
import com.remelearning.recording.service.RecordingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Recordings", description = "Recording ingestion: upload to S3, persist metadata, publish recording.uploaded")
@RestController
@RequestMapping("/api/v1/recordings")
@RequiredArgsConstructor
public class RecordingController {

	private final RecordingService recordingService;

	@Operation(summary = "Upload a recording file (multipart/form-data); stores it in S3, persists metadata, "
			+ "and publishes recording.uploaded for ai-service to consume")
	@PostMapping
	public ApiResponse<RecordingResponse> upload(
			@RequestParam MultipartFile file,
			@RequestParam String userId,
			@RequestParam(required = false, defaultValue = "en") String languageCode) {
		return ApiResponse.ok(recordingService.upload(file, userId, languageCode));
	}

	@Operation(summary = "Get a single recording's metadata by its recordingId")
	@GetMapping("/{recordingId}")
	public ApiResponse<RecordingResponse> getByRecordingId(@PathVariable String recordingId) {
		return ApiResponse.ok(recordingService.getByRecordingId(recordingId));
	}

	@Operation(summary = "List every recording uploaded by a given user, most recent first")
	@GetMapping("/user/{userId}")
	public ApiResponse<List<RecordingResponse>> getByUserId(@PathVariable String userId) {
		return ApiResponse.ok(recordingService.getByUserId(userId));
	}
}
