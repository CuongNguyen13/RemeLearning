package com.remelearning.common.ai.tts.google;

import com.remelearning.common.ai.tts.TtsAudio;
import com.remelearning.common.ai.tts.TtsClient;
import com.remelearning.common.ai.tts.TtsRequest;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Base64;

/**
 * Google Cloud Text-to-Speech-backed {@link TtsClient} implementation.
 * Registered by {@link GoogleCloudTtsClientConfig} when {@code reme.tts.provider=google} (the default);
 * other providers can be added later as sibling packages (e.g. {@code ai.tts.polly}) without touching callers.
 */
public class GoogleCloudTtsClient implements TtsClient {

	private static final String SYNTHESIZE_PATH = "/v1/text:synthesize?key={apiKey}";

	private final RestClient restClient;
	private final String apiKey;

	public GoogleCloudTtsClient(RestClient restClient, String apiKey) {
		this.restClient = restClient;
		this.apiKey = apiKey;
	}

	// Builds the Google TTS wire request from the vendor-neutral TtsRequest, POSTs it to
	// text:synthesize, then decodes the base64 audioContent back into raw bytes.
	@Override
	public TtsAudio synthesize(TtsRequest request) {
		GoogleTtsRequest body = new GoogleTtsRequest(
				new GoogleTtsRequest.Input(request.getText()),
				new GoogleTtsRequest.Voice(request.getLanguageCode(), request.getVoiceName()),
				new GoogleTtsRequest.AudioConfig(request.getAudioEncoding()));

		GoogleTtsResponse response = restClient.post()
				.uri(SYNTHESIZE_PATH, apiKey)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(GoogleTtsResponse.class);

		return toTtsAudio(request.getAudioEncoding(), response);
	}

	// Google returns the synthesized clip as a base64 string; an empty/missing audioContent means
	// the request was rejected (e.g. invalid voice name for the given languageCode).
	private TtsAudio toTtsAudio(String audioEncoding, GoogleTtsResponse response) {
		if (response == null || response.audioContent() == null || response.audioContent().isBlank()) {
			throw new IllegalStateException("Google Cloud TTS returned no audio content");
		}

		byte[] audioBytes = Base64.getDecoder().decode(response.audioContent());
		String mimeType = "LINEAR16".equalsIgnoreCase(audioEncoding) ? "audio/wav" : "audio/mpeg";
		return TtsAudio.builder().audioBytes(audioBytes).mimeType(mimeType).build();
	}
}
