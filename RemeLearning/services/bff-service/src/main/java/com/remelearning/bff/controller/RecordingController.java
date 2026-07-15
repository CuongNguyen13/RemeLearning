package com.remelearning.bff.controller;

import com.remelearning.bff.client.RecordingServiceClient;
import com.remelearning.bff.dto.RecordingDto;
import com.remelearning.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Tag(name = "Recordings", description = "Proxy for recording-service's upload endpoint")
@RestController
@RequestMapping("/api/v1/recordings")
@RequiredArgsConstructor
public class RecordingController {

	private final RecordingServiceClient recordingServiceClient;

	@Operation(summary = "Upload a recording; streamed straight through to recording-service without buffering the file in bff-service")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Mono<ApiResponse<RecordingDto>> upload(
			@RequestPart("file") FilePart file,
			@RequestPart("userId") String userId,
			@RequestPart(value = "languageCode", required = false) String languageCode) {
		return recordingServiceClient.upload(file, userId, languageCode);
	}
}
