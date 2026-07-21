package com.remelearning.common.ai.align.aiservice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.align.SentenceAlignmentClient;
import com.remelearning.common.ai.align.SentenceTiming;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.util.List;

/**
 * {@link SentenceAlignmentClient} backed by the Python ai-service, which transcribes the clip's
 * audio with Whisper word-level timestamps and matches the given sentences against that timeline.
 * Registered by {@link AiServiceSentenceAlignmentClientConfig}. Keeping the STT model in ai-service
 * means the JVM services stay free of the ML stack and only speak this small REST contract.
 */
public class AiServiceSentenceAlignmentClient implements SentenceAlignmentClient {

	private static final String ALIGN_PATH = "/api/v1/dictation/align-sentences";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public AiServiceSentenceAlignmentClient(RestClient restClient, ObjectMapper objectMapper) {
		this.restClient = restClient;
		this.objectMapper = objectMapper;
	}

	// Uploads the audio plus the ordered sentence list (JSON-encoded, since multipart/form-data has
	// no native list type) and maps ai-service's per-sentence timings back onto the neutral DTO.
	@Override
	public List<SentenceTiming> align(InputStream audio, String audioFilename, List<String> sentences) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("audio", new InputStreamResource(audio)).filename(audioFilename);
		builder.part("sentences", toJson(sentences));

		List<AiServiceSentenceTimingResponse> response = restClient.post()
				.uri(ALIGN_PATH)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(builder.build())
				.retrieve()
				.body(new ParameterizedTypeReference<List<AiServiceSentenceTimingResponse>>() {
				});

		if (response == null || response.size() != sentences.size()) {
			throw new IllegalStateException(
					"ai-service alignment returned %d timing(s) for %d sentence(s)"
							.formatted(response == null ? 0 : response.size(), sentences.size()));
		}
		return response.stream().map(timing -> new SentenceTiming(timing.startMs(), timing.endMs())).toList();
	}

	private String toJson(List<String> sentences) {
		try {
			return objectMapper.writeValueAsString(sentences);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize sentences for ai-service alignment", ex);
		}
	}
}
