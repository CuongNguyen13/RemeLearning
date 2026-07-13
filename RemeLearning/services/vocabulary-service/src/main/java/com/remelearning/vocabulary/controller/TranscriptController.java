package com.remelearning.vocabulary.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.vocabulary.dto.TranscriptResponse;
import com.remelearning.vocabulary.service.TranscriptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transcripts", description = "Transcripts persisted from ai-service's transcript.ready event")
@RestController
@RequestMapping("/api/v1/transcripts")
@RequiredArgsConstructor
public class TranscriptController {

	private final TranscriptService transcriptService;

	@Operation(summary = "Get the stored transcript for a recording, including timestamped/speaker-tagged segments")
	@GetMapping("/{recordingId}")
	public ApiResponse<TranscriptResponse> getByRecordingId(@PathVariable String recordingId) {
		return ApiResponse.ok(transcriptService.getByRecordingId(recordingId));
	}
}
