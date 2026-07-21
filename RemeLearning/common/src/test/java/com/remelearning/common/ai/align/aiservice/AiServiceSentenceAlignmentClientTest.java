package com.remelearning.common.ai.align.aiservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.ai.align.SentenceTiming;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiServiceSentenceAlignmentClientTest {

	private static final String BASE_URL = "http://ai-service:8000";
	private final ObjectMapper objectMapper = new ObjectMapper();

	private record Fixture(AiServiceSentenceAlignmentClient client, MockRestServiceServer server) {
	}

	// Builds a client whose RestClient is intercepted by a MockRestServiceServer.
	private Fixture newFixture() {
		RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		return new Fixture(new AiServiceSentenceAlignmentClient(builder.build(), objectMapper), server);
	}

	// Maps ai-service's per-sentence JSON array back onto SentenceTiming, in order.
	@Test
	void alignMapsAiServiceResponseOntoSentenceTimings() {
		Fixture fixture = newFixture();
		fixture.server().expect(requestTo(BASE_URL + "/api/v1/dictation/align-sentences"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(
						"[{\"start_ms\":0,\"end_ms\":500},{\"start_ms\":null,\"end_ms\":null}]",
						MediaType.APPLICATION_JSON));

		List<SentenceTiming> timings = fixture.client().align(
				new ByteArrayInputStream(new byte[] { 1, 2, 3 }), "clip.mp3", List.of("Hi.", "Bye."));

		assertThat(timings).containsExactly(new SentenceTiming(0, 500), new SentenceTiming(null, null));
		fixture.server().verify();
	}

	// A response with a different element count than the sentence list can't be positionally
	// correlated, so it's treated as a failure rather than silently misaligning sentences.
	@Test
	void alignThrowsWhenResponseSizeDoesNotMatchSentenceCount() {
		Fixture fixture = newFixture();
		fixture.server().expect(requestTo(BASE_URL + "/api/v1/dictation/align-sentences"))
				.andRespond(withSuccess("[{\"start_ms\":0,\"end_ms\":500}]", MediaType.APPLICATION_JSON));

		InputStream audio = new ByteArrayInputStream(new byte[] { 1 });
		assertThatThrownBy(() -> fixture.client().align(audio, "clip.mp3", List.of("Hi.", "Bye.")))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("1")
				.hasMessageContaining("2");
	}
}
