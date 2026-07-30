package com.remelearning.english.writing.suggestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.writing.domain.WritingSuggestion;
import com.remelearning.english.writing.domain.WritingTaskType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmNextSentenceSuggesterTest {

	private final AiContentClient aiContentClient = mock(AiContentClient.class);
	private final LlmNextSentenceSuggester suggester = new LlmNextSentenceSuggester(aiContentClient);

	@Test
	void parsesTheSuggestedIdeasStructuresAndPhrases() {
		stubLlm("""
				[{"ideaVi": "Nói về lý do bạn chọn công việc này.",
				  "structureHint": "The reason why + clause + is that + clause",
				  "usefulPhrases": ["pursue a career", "long-term goal"]}]""");

		List<WritingSuggestion> suggestions = suggester.suggest(
				WritingTaskType.COMPOSE, "Brief", "I work as a teacher.", "B1");

		assertThat(suggestions).singleElement().satisfies(suggestion -> {
			assertThat(suggestion.getIdeaVi()).isEqualTo("Nói về lý do bạn chọn công việc này.");
			assertThat(suggestion.getStructureHint()).contains("The reason why");
			assertThat(suggestion.getUsefulPhrases()).containsExactly("pursue a career", "long-term goal");
		});
	}

	@Test
	void usesTheRestrictedTranslationPromptSoAHintCannotBecomeTheAnswer() {
		stubLlm("""
				[{"ideaVi": "Dịch câu thứ hai.", "structureHint": "past perfect: had + V3", "usefulPhrases": ["move to"]}]""");

		suggester.suggest(WritingTaskType.TRANSLATE_VI_EN, "Tôi đã sống ở Hà Nội...", "I have lived...", "B1");

		ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
		verify(aiContentClient).completeJson(
				systemPrompt.capture(), anyString(), anyDouble(), anyInt(),
				org.mockito.ArgumentMatchers.eq(LlmNextSentenceSuggester.LlmSuggestion[].class));
		assertThat(systemPrompt.getValue())
				.contains("NEVER translate the next sentence")
				.contains("NEVER output a complete sentence in the target language");
	}

	@Test
	void composeUsesTheScaffoldingPromptInstead() {
		stubLlm("[]");

		suggester.suggest(WritingTaskType.COMPOSE, "Brief", "draft", "B1");

		ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
		verify(aiContentClient).completeJson(
				systemPrompt.capture(), anyString(), anyDouble(), anyInt(),
				org.mockito.ArgumentMatchers.eq(LlmNextSentenceSuggester.LlmSuggestion[].class));
		assertThat(systemPrompt.getValue()).contains("never write the next sentence for them");
	}

	@Test
	void theUserPromptNeverContainsAReferenceAnswerBecauseTheSuggesterIsNeverGivenOne() {
		stubLlm("[]");

		// The interface has no reference-answer parameter at all - this pins that down, since adding
		// one later would silently turn every translation hint into the model translation.
		suggester.suggest(WritingTaskType.TRANSLATE_EN_VI, "She had already left the office.", "Cô ấy đã...", "B2");

		ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
		verify(aiContentClient).completeJson(
				anyString(), userPrompt.capture(), anyDouble(), anyInt(),
				org.mockito.ArgumentMatchers.eq(LlmNextSentenceSuggester.LlmSuggestion[].class));
		assertThat(userPrompt.getValue())
				.contains("Source passage to translate")
				.contains("She had already left the office.")
				.doesNotContain("Reference answer");
	}

	@Test
	void tellsTheModelWhenTheLearnerHasNotStartedYet() {
		stubLlm("[]");

		suggester.suggest(WritingTaskType.COMPOSE, "Brief", "   ", "B1");

		ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
		verify(aiContentClient).completeJson(
				anyString(), userPrompt.capture(), anyDouble(), anyInt(),
				org.mockito.ArgumentMatchers.eq(LlmNextSentenceSuggester.LlmSuggestion[].class));
		assertThat(userPrompt.getValue()).contains("they need help starting");
	}

	@Test
	void dropsSuggestionsWithNothingUsefulToSay() {
		stubLlm("""
				[{"ideaVi": "  ", "structureHint": "x"},
				 {"ideaVi": null, "structureHint": "y"},
				 {"ideaVi": "Một ý hữu ích.", "structureHint": "z"}]""");

		List<WritingSuggestion> suggestions = suggester.suggest(WritingTaskType.COMPOSE, "Brief", "draft", null);

		assertThat(suggestions).singleElement()
				.satisfies(suggestion -> assertThat(suggestion.getIdeaVi()).isEqualTo("Một ý hữu ích."));
	}

	@Test
	void defaultsUsefulPhrasesToAnEmptyListRatherThanNull() {
		stubLlm("""
				[{"ideaVi": "Một ý.", "structureHint": "x"}]""");

		List<WritingSuggestion> suggestions = suggester.suggest(WritingTaskType.COMPOSE, "Brief", "draft", null);

		assertThat(suggestions).singleElement()
				.satisfies(suggestion -> assertThat(suggestion.getUsefulPhrases()).isEmpty());
	}

	@Test
	void returnsNoSuggestionsWhenTheLlmCallFails() {
		when(aiContentClient.completeJson(
				anyString(), anyString(), anyDouble(), anyInt(),
				org.mockito.ArgumentMatchers.eq(LlmNextSentenceSuggester.LlmSuggestion[].class)))
				.thenThrow(new AiContentException("boom"));

		// A failed hint must never break the learner's writing session.
		assertThat(suggester.suggest(WritingTaskType.COMPOSE, "Brief", "draft", "B1")).isEmpty();
	}

	private void stubLlm(String json) {
		when(aiContentClient.completeJson(
				anyString(), anyString(), anyDouble(), anyInt(),
				org.mockito.ArgumentMatchers.eq(LlmNextSentenceSuggester.LlmSuggestion[].class)))
				.thenAnswer(invocation ->
						new ObjectMapper().readValue(json, LlmNextSentenceSuggester.LlmSuggestion[].class));
	}
}
