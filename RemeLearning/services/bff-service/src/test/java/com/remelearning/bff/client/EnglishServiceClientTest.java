package com.remelearning.bff.client;

import com.remelearning.bff.dto.FinishSpeakingSectionResponse;
import com.remelearning.bff.dto.ListeningAnswerItemDto;
import com.remelearning.bff.dto.ListeningLibraryHistoryEntryDto;
import com.remelearning.bff.dto.ListeningLibrarySectionDto;
import com.remelearning.bff.dto.ListeningLibraryTopicDto;
import com.remelearning.bff.dto.SentenceAttemptResultDto;
import com.remelearning.bff.dto.SpeakingLibraryHistoryEntryDto;
import com.remelearning.bff.dto.SpeakingLibrarySectionDto;
import com.remelearning.bff.dto.SubmitListeningAnswersRequest;
import com.remelearning.bff.dto.SubmitListeningAnswersResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies the listening/speaking-library proxy methods added to {@link EnglishServiceClient}: each
 * one hits the expected downstream URI/method and unwraps the {@code ApiResponse<T>} envelope into
 * the plain bff DTOs. No MockWebServer/WireMock exists in this repo (per the client/*Client mocking
 * convention used elsewhere), so a stub {@link ExchangeFunction} stands in for the real HTTP
 * transport - it avoids opening a real socket while still exercising WebClient's real request
 * building/response decoding path.
 */
class EnglishServiceClientTest {

	@Test
	void getListeningLibraryTopicsDecodesTopicListFromApiResponseEnvelope() {
		String json = "{\"success\":true,\"data\":[{\"id\":1,\"name\":\"Travel\",\"level\":\"A1\",\"status\":\"UNLOCKED\"}]}";
		EnglishServiceClient client = clientReturning(request -> {
			assertThat(request.method()).isEqualTo(HttpMethod.GET);
			assertThat(request.url().toString()).endsWith("/api/v1/learn/listening/library/user-1/topics");
		}, json);

		StepVerifier.create(client.getListeningLibraryTopics("user-1"))
				.assertNext(topics -> {
					assertThat(topics).hasSize(1);
					ListeningLibraryTopicDto topic = topics.get(0);
					assertThat(topic.getId()).isEqualTo(1L);
					assertThat(topic.getName()).isEqualTo("Travel");
					assertThat(topic.getStatus()).isEqualTo("UNLOCKED");
				})
				.verifyComplete();
	}

	@Test
	void startListeningLibrarySectionDecodesSectionWithQuestions() {
		String json = "{\"success\":true,\"data\":{\"sectionId\":100,\"passageText\":\"A short passage.\","
				+ "\"audioUrl\":\"https://example.com/audio.mp3\",\"questions\":["
				+ "{\"questionId\":1,\"questionText\":\"Where?\",\"options\":[\"Paris\",\"Rome\"]}]}}";
		EnglishServiceClient client = clientReturning(request -> {
			assertThat(request.method()).isEqualTo(HttpMethod.POST);
			assertThat(request.url().toString()).endsWith("/api/v1/learn/listening/library/user-1/topics/5/sections");
		}, json);

		StepVerifier.create(client.startListeningLibrarySection("user-1", 5L))
				.assertNext((ListeningLibrarySectionDto section) -> {
					assertThat(section.getSectionId()).isEqualTo(100L);
					assertThat(section.getQuestions()).hasSize(1);
					assertThat(section.getQuestions().get(0).getOptions()).containsExactly("Paris", "Rome");
				})
				.verifyComplete();
	}

	@Test
	void submitListeningLibraryAnswersDecodesScoringResult() {
		String json = "{\"success\":true,\"data\":{\"score\":1.0,\"correctCount\":2,\"totalQuestions\":2,"
				+ "\"topicPassed\":true,\"nextTopicId\":6,\"nextTopicUnlocked\":true}}";
		EnglishServiceClient client = clientReturning(request -> {
			assertThat(request.method()).isEqualTo(HttpMethod.POST);
			assertThat(request.url().toString()).endsWith("/api/v1/learn/listening/library/user-1/sections/100/answers");
		}, json);

		SubmitListeningAnswersRequest request = new SubmitListeningAnswersRequest();
		ListeningAnswerItemDto answer = new ListeningAnswerItemDto();
		answer.setQuestionId(1L);
		answer.setSelectedOption("A");
		request.setAnswers(List.of(answer));

		StepVerifier.create(client.submitListeningLibraryAnswers("user-1", 100L, request))
				.assertNext((SubmitListeningAnswersResponse result) -> {
					assertThat(result.isTopicPassed()).isTrue();
					assertThat(result.getNextTopicId()).isEqualTo(6L);
					assertThat(result.isNextTopicUnlocked()).isTrue();
				})
				.verifyComplete();
	}

	@Test
	void getListeningLibraryHistoryDecodesAttemptList() {
		String json = "{\"success\":true,\"data\":[{\"id\":9,\"sectionId\":100,\"score\":0.8,"
				+ "\"correctCount\":4,\"totalQuestions\":5}]}";
		EnglishServiceClient client = clientReturning(request -> {
			assertThat(request.method()).isEqualTo(HttpMethod.GET);
			assertThat(request.url().toString()).endsWith("/api/v1/learn/listening/library/user-1/sections/history");
		}, json);

		StepVerifier.create(client.getListeningLibraryHistory("user-1"))
				.assertNext(history -> {
					assertThat(history).hasSize(1);
					ListeningLibraryHistoryEntryDto entry = history.get(0);
					assertThat(entry.getSectionId()).isEqualTo(100L);
					assertThat(entry.getCorrectCount()).isEqualTo(4);
				})
				.verifyComplete();
	}

	@Test
	void getSpeakingLibraryTopicsDecodesTopicListFromApiResponseEnvelope() {
		String json = "{\"success\":true,\"data\":[{\"id\":1,\"name\":\"Travel\",\"level\":\"A1\",\"status\":\"LOCKED\"}]}";
		EnglishServiceClient client = clientReturning(request -> {
			assertThat(request.method()).isEqualTo(HttpMethod.GET);
			assertThat(request.url().toString()).endsWith("/api/v1/learn/speaking/library/user-1/topics");
		}, json);

		StepVerifier.create(client.getSpeakingLibraryTopics("user-1"))
				.assertNext(topics -> {
					assertThat(topics).hasSize(1);
					assertThat(topics.get(0).getStatus()).isEqualTo("LOCKED");
				})
				.verifyComplete();
	}

	@Test
	void startSpeakingLibrarySectionDecodesSectionWithSentences() {
		String json = "{\"success\":true,\"data\":{\"sectionId\":200,\"sentences\":["
				+ "{\"sentenceId\":1,\"sentenceText\":\"I like travelling.\",\"ipa\":\"/aɪ laɪk/\","
				+ "\"sampleAudioUrl\":\"https://example.com/s1.mp3\"}]}}";
		EnglishServiceClient client = clientReturning(request -> {
			assertThat(request.method()).isEqualTo(HttpMethod.POST);
			assertThat(request.url().toString()).endsWith("/api/v1/learn/speaking/library/user-1/topics/7/sections");
		}, json);

		StepVerifier.create(client.startSpeakingLibrarySection("user-1", 7L))
				.assertNext((SpeakingLibrarySectionDto section) -> {
					assertThat(section.getSectionId()).isEqualTo(200L);
					assertThat(section.getSentences()).hasSize(1);
					assertThat(section.getSentences().get(0).getIpa()).isEqualTo("/aɪ laɪk/");
				})
				.verifyComplete();
	}

	@Test
	void submitSpeakingSentenceAttemptStreamsMultipartAudioAndDecodesScoringResult() {
		String json = "{\"success\":true,\"data\":{\"sentenceId\":1,\"phonemeScore\":0.9,\"wordScore\":0.85,"
				+ "\"passed\":true,\"transcript\":\"I like travelling.\"}}";
		EnglishServiceClient client = clientReturning(request -> {
			assertThat(request.method()).isEqualTo(HttpMethod.POST);
			assertThat(request.url().toString())
					.endsWith("/api/v1/learn/speaking/library/user-1/sections/200/sentences/1/attempts");
			assertThat(request.headers().getContentType().toString()).startsWith("multipart/form-data");
		}, json);

		FilePart audio = mock(FilePart.class);
		DataBuffer buffer = new DefaultDataBufferFactory().wrap("fake-audio-bytes".getBytes());
		when(audio.content()).thenReturn(Flux.just(buffer));
		when(audio.filename()).thenReturn("attempt.wav");

		StepVerifier.create(client.submitSpeakingSentenceAttempt("user-1", 200L, 1L, audio))
				.assertNext((SentenceAttemptResultDto result) -> {
					assertThat(result.isPassed()).isTrue();
					assertThat(result.getPhonemeScore()).isEqualTo(0.9);
				})
				.verifyComplete();
	}

	@Test
	void finishSpeakingLibrarySectionDecodesFinishResult() {
		String json = "{\"success\":true,\"data\":{\"totalSentences\":5,\"passedSentences\":5,\"passed\":true,"
				+ "\"nextTopicId\":8,\"nextTopicUnlocked\":true}}";
		EnglishServiceClient client = clientReturning(request -> {
			assertThat(request.method()).isEqualTo(HttpMethod.POST);
			assertThat(request.url().toString()).endsWith("/api/v1/learn/speaking/library/user-1/sections/200/finish");
		}, json);

		StepVerifier.create(client.finishSpeakingLibrarySection("user-1", 200L))
				.assertNext((FinishSpeakingSectionResponse result) -> {
					assertThat(result.isPassed()).isTrue();
					assertThat(result.getNextTopicId()).isEqualTo(8L);
				})
				.verifyComplete();
	}

	@Test
	void getSpeakingLibraryHistoryDecodesAttemptList() {
		String json = "{\"success\":true,\"data\":[{\"id\":11,\"sectionId\":200,\"sentenceId\":1,"
				+ "\"phonemeScore\":0.9,\"wordScore\":0.85}]}";
		EnglishServiceClient client = clientReturning(request -> {
			assertThat(request.method()).isEqualTo(HttpMethod.GET);
			assertThat(request.url().toString()).endsWith("/api/v1/learn/speaking/library/user-1/sections/history");
		}, json);

		StepVerifier.create(client.getSpeakingLibraryHistory("user-1"))
				.assertNext(history -> {
					assertThat(history).hasSize(1);
					SpeakingLibraryHistoryEntryDto entry = history.get(0);
					assertThat(entry.getSentenceId()).isEqualTo(1L);
					assertThat(entry.getPhonemeScore()).isEqualTo(0.9);
				})
				.verifyComplete();
	}

	// Builds an EnglishServiceClient backed by a WebClient whose transport is a stub ExchangeFunction:
	// it runs the given assertion against the outgoing request, then always answers with the given
	// canned JSON body wrapped as a 200 OK - so every test above exercises the real request-building
	// and response-decoding code in EnglishServiceClient without any network I/O.
	private EnglishServiceClient clientReturning(
			java.util.function.Consumer<org.springframework.web.reactive.function.client.ClientRequest> requestAssertions,
			String responseJson) {
		ExchangeFunction exchangeFunction = request -> {
			requestAssertions.accept(request);
			return reactor.core.publisher.Mono.just(ClientResponse.create(HttpStatus.OK)
					.header("Content-Type", "application/json")
					.body(responseJson)
					.build());
		};
		WebClient webClient = WebClient.builder().baseUrl("http://english-service").exchangeFunction(exchangeFunction).build();
		return new EnglishServiceClient(webClient);
	}
}
