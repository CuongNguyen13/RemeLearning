package com.remelearning.common.ai.audio.aiservice;

import com.remelearning.common.ai.audio.AudioTranscodeClient;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;

import java.io.InputStream;

/**
 * {@link AudioTranscodeClient} backed by the Python ai-service's
 * {@code POST /api/v1/audio/transcode/opus} (PyAV/FFmpeg libopus encoding). Registered by
 * {@link AiServiceAudioTranscodeClientConfig}.
 */
public class AiServiceAudioTranscodeClient implements AudioTranscodeClient {

	private static final String TRANSCODE_PATH = "/api/v1/audio/transcode/opus";

	private final RestClient restClient;

	public AiServiceAudioTranscodeClient(RestClient restClient) {
		this.restClient = restClient;
	}

	@Override
	public byte[] toOpus(InputStream audio, String filename) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.part("audio", new InputStreamResource(audio)).filename(filename == null ? "audio" : filename);

		byte[] opusBytes = restClient.post()
				.uri(TRANSCODE_PATH)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(builder.build())
				.retrieve()
				.body(byte[].class);

		if (opusBytes == null) {
			throw new IllegalStateException("ai-service returned no transcoded audio");
		}
		return opusBytes;
	}
}
