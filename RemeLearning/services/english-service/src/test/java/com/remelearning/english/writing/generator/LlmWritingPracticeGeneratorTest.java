package com.remelearning.english.writing.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.writing.domain.WritingTaskType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmWritingPracticeGeneratorTest {

	private final AiContentClient aiContentClient = mock(AiContentClient.class);
	private final LlmWritingPracticeGenerator generator = new LlmWritingPracticeGenerator(aiContentClient, 8000);

	@Test
	void parsesTheGeneratedPromptAndReferenceAnswer() {
		stubLlm("""
				{"topic": "Daily routine",
				 "promptText": "Viết một đoạn văn tiếng Anh (tối thiểu 80 từ) về thói quen hằng ngày.",
				 "referenceAnswer": "I usually wake up at six."}""");

		GeneratedWritingPractice generated = generator.generate(
				WritingTaskType.COMPOSE, List.of("present simple"), "B1", null);

		assertThat(generated.topic()).isEqualTo("Daily routine");
		assertThat(generated.promptText()).contains("tối thiểu 80 từ");
		assertThat(generated.referenceAnswer()).isEqualTo("I usually wake up at six.");
	}

	@Test
	void passesTheTargetLabelsAndTaskTypeIntoThePrompt() {
		stubLlm("""
				{"topic": "t", "promptText": "p", "referenceAnswer": "r"}""");

		generator.generate(WritingTaskType.TRANSLATE_VI_EN, List.of("past perfect", "collocation: make/do"), "B2", "IELTS");

		verify(aiContentClient).completeJson(
				anyString(), contains("past perfect"), anyDouble(), anyInt(),
				eq(LlmWritingPracticeGenerator.LlmPayload.class));
		verify(aiContentClient).completeJson(
				anyString(), contains("TRANSLATE_VI_EN"), anyDouble(), anyInt(),
				eq(LlmWritingPracticeGenerator.LlmPayload.class));
	}

	@Test
	void requiresTheGeneratedPassageToStayOnOneSingleSituation() {
		stubLlm("""
				{"topic": "t", "promptText": "p", "referenceAnswer": "r"}""");

		generator.generate(WritingTaskType.TRANSLATE_VI_EN, List.of("past perfect", "past continuous"), "B1", null);

		ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
		verify(aiContentClient).completeJson(
				systemPrompt.capture(), anyString(), anyDouble(), anyInt(),
				eq(LlmWritingPracticeGenerator.LlmPayload.class));
		// Without these instructions the model treats N sentences + N target labels as N unrelated
		// example sentences, producing a passage that changes subject on every line.
		assertThat(systemPrompt.getValue())
				.contains("ONE CONTINUOUS TEXT ABOUT ONE SINGLE SITUATION")
				.contains("SAME people, place and time frame")
				.contains("drop that structure");
	}

	@Test
	void acceptsSnakeCaseKeysIfTheModelIgnoresTheCamelCaseContract() {
		stubLlm("""
				{"topic": "t", "prompt_text": "Dịch đoạn sau...", "reference_answer": "Reference."}""");

		GeneratedWritingPractice generated = generator.generate(WritingTaskType.TRANSLATE_EN_VI, List.of(), null, null);

		// Without the aliases this would parse to a null promptText and be rejected as an empty
		// generation, losing a perfectly good generated task.
		assertThat(generated.promptText()).isEqualTo("Dịch đoạn sau...");
		assertThat(generated.referenceAnswer()).isEqualTo("Reference.");
	}

	@Test
	void throwsRatherThanFallingBackToATemplateWhenTheLlmCallFails() {
		when(aiContentClient.completeJson(
				anyString(), anyString(), anyDouble(), anyInt(), eq(LlmWritingPracticeGenerator.LlmPayload.class)))
				.thenThrow(new AiContentException("boom"));

		assertThatThrownBy(() -> generator.generate(WritingTaskType.TRANSLATE_VI_EN, List.of(), "B1", null))
				.isInstanceOf(AiContentException.class);
	}

	@Test
	void throwsWhenTheLlmReturnsNoPromptText() {
		stubLlm("""
				{"topic": "t", "promptText": "   ", "referenceAnswer": "r"}""");

		assertThatThrownBy(() -> generator.generate(WritingTaskType.COMPOSE, List.of(), null, null))
				.isInstanceOf(AiContentException.class);
	}

	@Test
	void usesADefaultTopicWhenTheModelOmitsOne() {
		stubLlm("""
				{"promptText": "Viết ...", "referenceAnswer": "r"}""");

		GeneratedWritingPractice generated = generator.generate(WritingTaskType.COMPOSE, List.of(), "B1", null);

		assertThat(generated.topic()).isEqualTo("B1 writing task");
	}

	@Test
	void tellsTheModelExactlyHowManySentencesTheExamStyleCallsFor() {
		stubLlm("""
				{"topic": "t", "promptText": "p", "referenceAnswer": "r"}""");

		generator.generate(WritingTaskType.TRANSLATE_VI_EN, List.of(), "B1", "TOEIC");

		ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
		verify(aiContentClient).completeJson(
				anyString(), userPrompt.capture(), anyDouble(), anyInt(),
				eq(LlmWritingPracticeGenerator.LlmPayload.class));
		// The count is decided in Java, not left to the model - that is what makes a TOEIC passage
		// actually shorter than an IELTS one instead of the model guessing from the label.
		String sent = userPrompt.getValue();
		assertThat(sent).contains("Sentences the passage must have:");
		int count = Integer.parseInt(
				sent.split("Sentences the passage must have: ")[1].lines().findFirst().orElseThrow().trim());
		assertThat(count).isBetween(
				WritingExamProfile.TOEIC.minSentences(), WritingExamProfile.TOEIC.maxSentences());
	}

	@Test
	void passesTheExamStylesRegisterAndSubjectAreaThroughNormalized() {
		stubLlm("""
				{"topic": "t", "promptText": "p", "referenceAnswer": "r"}""");

		generator.generate(WritingTaskType.TRANSLATE_EN_VI, List.of(), null, "ielts");

		ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
		verify(aiContentClient).completeJson(
				anyString(), userPrompt.capture(), anyDouble(), anyInt(),
				eq(LlmWritingPracticeGenerator.LlmPayload.class));
		assertThat(userPrompt.getValue())
				.contains("Exam style: IELTS")
				.contains("Register: " + WritingExamProfile.IELTS.registerHint())
				.contains("Suggested subject area:");
	}

	@Test
	void asksForATextFormatOnlyForComposeTasks() {
		stubLlm("""
				{"topic": "t", "promptText": "p", "referenceAnswer": "r"}""");

		generator.generate(WritingTaskType.COMPOSE, List.of(), null, "TOEIC");
		ArgumentCaptor<String> composePrompt = ArgumentCaptor.forClass(String.class);
		verify(aiContentClient).completeJson(
				anyString(), composePrompt.capture(), anyDouble(), anyInt(),
				eq(LlmWritingPracticeGenerator.LlmPayload.class));
		// COMPOSE is the only mode where "which kind of text" is a choice - a translation task's format
		// is fixed by its source passage.
		assertThat(composePrompt.getValue()).contains("Text format to ask for:");
	}

	@Test
	void aTranslationTaskIsNeverAskedForATextFormat() {
		stubLlm("""
				{"topic": "t", "promptText": "p", "referenceAnswer": "r"}""");

		generator.generate(WritingTaskType.TRANSLATE_VI_EN, List.of(), null, "TOEIC");

		ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
		verify(aiContentClient).completeJson(
				anyString(), userPrompt.capture(), anyDouble(), anyInt(),
				eq(LlmWritingPracticeGenerator.LlmPayload.class));
		assertThat(userPrompt.getValue()).doesNotContain("Text format to ask for:");
	}

	@Test
	void anUnknownExamStyleStillProducesAUsablePromptViaTheGeneralProfile() {
		stubLlm("""
				{"topic": "t", "promptText": "p", "referenceAnswer": "r"}""");

		generator.generate(WritingTaskType.TRANSLATE_VI_EN, List.of(), null, "Cambridge FCE");

		ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
		verify(aiContentClient).completeJson(
				anyString(), userPrompt.capture(), anyDouble(), anyInt(),
				eq(LlmWritingPracticeGenerator.LlmPayload.class));
		// The label is passed through untouched so the model can still use it, while the concrete
		// length/register fall back to the General profile.
		assertThat(userPrompt.getValue())
				.contains("Exam style: Cambridge FCE")
				.contains("Register: " + WritingExamProfile.GENERAL.registerHint());
	}

	private void stubLlm(String json) {
		when(aiContentClient.completeJson(
				anyString(), anyString(), anyDouble(), anyInt(), eq(LlmWritingPracticeGenerator.LlmPayload.class)))
				.thenAnswer(invocation ->
						new ObjectMapper().readValue(json, LlmWritingPracticeGenerator.LlmPayload.class));
	}
}
