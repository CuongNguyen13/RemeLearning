package com.remelearning.common.ai.tts.supertonic;

import com.remelearning.common.ai.tts.TtsAudio;
import com.remelearning.common.ai.tts.TtsClient;
import com.remelearning.common.ai.tts.TtsRequest;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.Base64;

/**
 * {@link TtsClient} backed by the Python ai-service, which runs the Supertonic on-device (ONNX/CPU)
 * TTS model. Registered by {@link SupertonicTtsClientConfig} when {@code reme.tts.provider=supertonic}
 * (the default). Keeping the model in ai-service means the JVM services stay free of the ML stack and
 * only speak this small REST contract.
 */
public class SupertonicTtsClient implements TtsClient {

	private static final String SYNTHESIZE_PATH = "/api/v1/tts/synthesize";
	private static final String DEFAULT_LANG = "en";

	private final RestClient restClient;

	public SupertonicTtsClient(RestClient restClient) {
		this.restClient = restClient;
	}

	// Maps the neutral request onto ai-service's TTS endpoint and decodes the base64 audio it returns;
	// a null languageCode falls back to English, and a null voice lets ai-service pick its default.
	@Override
	public TtsAudio synthesize(TtsRequest request) {
		SupertonicTtsResponse response = restClient.post()
				.uri(SYNTHESIZE_PATH)
				.contentType(MediaType.APPLICATION_JSON)
				.body(new SupertonicTtsRequest(
						request.getText(),
						request.getLanguageCode() == null ? DEFAULT_LANG : request.getLanguageCode(),
						request.getVoice()))
				.retrieve()
				.body(SupertonicTtsResponse.class);

		return toTtsAudio(response);
	}

	// An empty/missing audio_base64 means ai-service couldn't synthesize (model not loaded, bad input);
	// surface it as an IllegalStateException so callers can degrade rather than ship silent/empty audio.
	private TtsAudio toTtsAudio(SupertonicTtsResponse response) {
		if (response == null || response.audioBase64() == null || response.audioBase64().isBlank()) {
			throw new IllegalStateException("Supertonic TTS (ai-service) returned no audio content");
		}

		byte[] audioBytes = Base64.getDecoder().decode(response.audioBase64());
		String mimeType = response.mimeType() == null || response.mimeType().isBlank()
				? "audio/wav" : response.mimeType();
		return TtsAudio.builder().audioBytes(audioBytes).mimeType(mimeType).sampleRate(response.sampleRate()).build();
	}
}
