package com.remelearning.bff.client;

import com.remelearning.bff.dto.RecordingDto;
import com.remelearning.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.client.MultipartBodyBuilder;
import reactor.core.publisher.Mono;

import java.util.List;

/** Thin wrapper around recording-service's REST API (recording metadata + the upload proxy). */
@Slf4j
@Component
public class RecordingServiceClient {

	private final WebClient recordingServiceClient;

	public RecordingServiceClient(@Qualifier("recordingServiceClient") WebClient recordingServiceClient) {
		this.recordingServiceClient = recordingServiceClient;
	}

	/** Fetches all recordings uploaded by a given learner. */
	public Mono<List<RecordingDto>> getRecordingsByUser(String userId) {
		return recordingServiceClient.get()
				.uri("/api/v1/recordings/user/{userId}", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<RecordingDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch recordings for userId={}", userId, ex));
	}

	/**
	 * Streams an uploaded file part straight through to recording-service's multipart upload
	 * endpoint without buffering it in memory - the file's content is re-published as an
	 * async multipart part (see Spring WebClient's multipart-proxy pattern) rather than read
	 * into a byte array first.
	 */
	public Mono<ApiResponse<RecordingDto>> upload(FilePart file, String userId, String languageCode) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.asyncPart("file", file.content(), DataBuffer.class)
				.headers(headers -> headers.setContentDispositionFormData("file", file.filename()));
		builder.part("userId", userId);
		if (languageCode != null && !languageCode.isBlank()) {
			builder.part("languageCode", languageCode);
		}
		return recordingServiceClient.post()
				.uri("/api/v1/recordings")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(BodyInserters.fromMultipartData(builder.build()))
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<RecordingDto>>() {})
				.doOnError(ex -> log.error("Failed to proxy recording upload for userId={}", userId, ex));
	}
}
