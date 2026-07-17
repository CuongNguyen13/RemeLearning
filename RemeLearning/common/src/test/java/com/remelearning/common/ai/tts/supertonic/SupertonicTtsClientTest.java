package com.remelearning.common.ai.tts.supertonic;

import com.remelearning.common.ai.tts.TtsAudio;
import com.remelearning.common.ai.tts.TtsRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupertonicTtsClientTest {

	private static final String BASE_URL = "http://ai-service:8000";

	// Builds a SupertonicTtsClient whose RestClient is intercepted by a MockRestServiceServer.
	private record Fixture(SupertonicTtsClient client, MockRestServiceServer server) {
	}

	private Fixture newFixture() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		return new Fixture(new SupertonicTtsClient(builder.build()), server);
	}

	// Posts text/lang/voice to the ai-service TTS endpoint and decodes the returned base64 into bytes.
	@Test
	void synthesizeDecodesBase64AudioFromAiService() {
		Fixture fixture = newFixture();
		String base64 = java.util.Base64.getEncoder().encodeToString("wav-bytes".getBytes(StandardCharsets.UTF_8));
		fixture.server().expect(requestTo(BASE_URL + "/api/v1/tts/synthesize"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.text").value("Practice this sentence."))
				.andExpect(jsonPath("$.lang").value("en"))
				.andExpect(jsonPath("$.voice").value("F1"))
				.andRespond(withSuccess(
						"{\"audio_base64\":\"" + base64 + "\",\"mime_type\":\"audio/wav\",\"sample_rate\":44100}",
						MediaType.APPLICATION_JSON));

		TtsAudio audio = fixture.client().synthesize(TtsRequest.builder()
				.text("Practice this sentence.").languageCode("en").voice("F1").build());

		assertThat(new String(audio.getAudioBytes(), StandardCharsets.UTF_8)).isEqualTo("wav-bytes");
		assertThat(audio.getMimeType()).isEqualTo("audio/wav");
		assertThat(audio.getSampleRate()).isEqualTo(44100);
		fixture.server().verify();
	}

	// A null languageCode defaults to "en" in the request sent to ai-service.
	@Test
	void synthesizeDefaultsLanguageToEnglish() {
		Fixture fixture = newFixture();
		String base64 = java.util.Base64.getEncoder().encodeToString("x".getBytes(StandardCharsets.UTF_8));
		fixture.server().expect(requestTo(BASE_URL + "/api/v1/tts/synthesize"))
				.andExpect(jsonPath("$.lang").value("en"))
				.andRespond(withSuccess(
						"{\"audio_base64\":\"" + base64 + "\",\"mime_type\":\"audio/wav\",\"sample_rate\":44100}",
						MediaType.APPLICATION_JSON));

		fixture.client().synthesize(TtsRequest.builder().text("hi").build());

		fixture.server().verify();
	}

	// An empty audio_base64 (ai-service couldn't synthesize) surfaces as IllegalStateException.
	@Test
	void synthesizeThrowsWhenNoAudioReturned() {
		Fixture fixture = newFixture();
		fixture.server().expect(requestTo(BASE_URL + "/api/v1/tts/synthesize"))
				.andRespond(withSuccess(
						"{\"audio_base64\":\"\",\"mime_type\":\"audio/wav\",\"sample_rate\":0}",
						MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> fixture.client().synthesize(TtsRequest.builder().text("hi").build()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("no audio content");
	}
}
