package com.remelearning.english.listening.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.common.exception.BusinessException;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.listening.domain.ListeningQuestionType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmListeningPracticeGeneratorTest {

	private final AiContentClient aiContentClient = mock(AiContentClient.class);
	private final LlmListeningPracticeGenerator generator = new LlmListeningPracticeGenerator(aiContentClient, 1300, 600);

	@Test
	void generateParsesEveryPassageOfTheSessionFromOneLlmCall() {
		stubLlm("""
				{"passages": [
				  {"topic": "Airport", "lines": [{"speaker": "A", "text": "Flight 204 is boarding.", "translation": "Chuyến 204 đang lên máy bay."}],
				   "questions": [{"type": "MCQ", "skill": "main-idea", "prompt": "What is this?", "options": ["A flight", "A train"], "answer": "A flight", "explanation": "Rõ ràng."},
				                 {"type": "KEYWORD", "skill": "keyword", "prompt": "Which word?", "options": null, "answer": "boarding", "explanation": "Nghe kỹ."}]},
				  {"topic": "Hotel", "lines": [{"speaker": "A", "text": "Your room isn't ready.", "translation": null}, {"speaker": "B", "text": "That's fine.", "translation": null}],
				   "questions": [{"type": "OPEN", "skill": "open-response", "prompt": "Why?", "options": null, "answer": "model", "explanation": "Giải thích."}]}
				]}
				""");

		List<GeneratedListeningPractice> passages = generator.generate(request(2));

		assertThat(passages).hasSize(2);
		assertThat(passages).extracting(GeneratedListeningPractice::topic).containsExactly("Airport", "Hotel");
		assertThat(passages.get(0).lines().get(0).translation()).isEqualTo("Chuyến 204 đang lên máy bay.");
		assertThat(passages.get(0).questions()).extracting(q -> q.getType())
				.containsExactly(ListeningQuestionType.MCQ, ListeningQuestionType.KEYWORD);
		assertThat(passages.get(1).lines()).hasSize(2);
	}

	@Test
	void generatePutsTheAlreadyPractisedTopicsAndTheRequestedCountIntoThePrompt() {
		stubLlm("""
				{"passages": [{"topic": "Hotel", "lines": [{"speaker": "A", "text": "Hi.", "translation": null}],
				   "questions": [{"type": "MCQ", "skill": "main-idea", "prompt": "p", "options": ["a", "b"], "answer": "a", "explanation": "e"}]}]}
				""");

		generator.generate(new ListeningSessionRequest(
				List.of("boarding"), "B1", "TOEIC", "vi", List.of("Airport", "Weather"), 6));

		ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
		verify(aiContentClient).completeJson(anyString(), userPrompt.capture(), anyDouble(), anyInt(),
				eq(LlmListeningPracticeGenerator.LlmPayload.class));
		assertThat(userPrompt.getValue())
				.contains("Passage count: 6")
				.contains("boarding")
				.contains("Airport")
				.contains("Weather")
				.contains("Level: B1");
	}

	@Test
	void generateSkipsAPassageTheModelReturnedWithNoQuestions() {
		stubLlm("""
				{"passages": [
				  {"topic": "Airport", "lines": [{"speaker": "A", "text": "Flight 204 is boarding.", "translation": null}], "questions": []},
				  {"topic": "Hotel", "lines": [{"speaker": "A", "text": "Your room isn't ready.", "translation": null}],
				   "questions": [{"type": "MCQ", "skill": "main-idea", "prompt": "p", "options": ["a", "b"], "answer": "a", "explanation": "e"}]}
				]}
				""");

		List<GeneratedListeningPractice> passages = generator.generate(request(2));

		assertThat(passages).extracting(GeneratedListeningPractice::topic).containsExactly("Hotel");
	}

	@Test
	void generateThrowsWhenTheLlmFails() {
		when(aiContentClient.completeJson(anyString(), anyString(), anyDouble(), anyInt(),
				eq(LlmListeningPracticeGenerator.LlmPayload.class)))
				.thenThrow(new AiContentException("LLM call failed", new RuntimeException("boom")));

		assertThatThrownBy(() -> generator.generate(request(7)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("LLM call failed");
	}

	private ListeningSessionRequest request(int passageCount) {
		return new ListeningSessionRequest(List.of(), "B1", null, null, List.of(), passageCount);
	}

	private void stubLlm(String llmJson) {
		when(aiContentClient.completeJson(anyString(), anyString(), anyDouble(), anyInt(),
				eq(LlmListeningPracticeGenerator.LlmPayload.class)))
				.thenAnswer(invocation -> new ObjectMapper()
						.readValue(llmJson, LlmListeningPracticeGenerator.LlmPayload.class));
	}
}
