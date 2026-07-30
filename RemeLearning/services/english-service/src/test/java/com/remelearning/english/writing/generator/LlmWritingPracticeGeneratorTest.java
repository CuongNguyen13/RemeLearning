package com.remelearning.english.writing.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.writing.domain.WritingTaskType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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
	private final LlmWritingPracticeGenerator generator = new LlmWritingPracticeGenerator(aiContentClient);

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
	void acceptsSnakeCaseKeysIfTheModelIgnoresTheCamelCaseContract() {
		stubLlm("""
				{"topic": "t", "prompt_text": "Dịch đoạn sau...", "reference_answer": "Reference."}""");

		GeneratedWritingPractice generated = generator.generate(WritingTaskType.TRANSLATE_EN_VI, List.of(), null, null);

		// Without the aliases this would parse to a null promptText and silently fall back to the
		// template, losing a perfectly good generated task.
		assertThat(generated.promptText()).isEqualTo("Dịch đoạn sau...");
		assertThat(generated.referenceAnswer()).isEqualTo("Reference.");
	}

	@Test
	void fallsBackToATemplateWhenTheLlmCallFails() {
		when(aiContentClient.completeJson(
				anyString(), anyString(), anyDouble(), anyInt(), eq(LlmWritingPracticeGenerator.LlmPayload.class)))
				.thenThrow(new AiContentException("boom"));

		GeneratedWritingPractice generated = generator.generate(WritingTaskType.TRANSLATE_VI_EN, List.of(), "B1", null);

		assertThat(generated.promptText()).isNotBlank();
		assertThat(generated.referenceAnswer()).isNotBlank();
	}

	@Test
	void fallsBackWhenTheLlmReturnsNoPromptText() {
		stubLlm("""
				{"topic": "t", "promptText": "   ", "referenceAnswer": "r"}""");

		GeneratedWritingPractice generated = generator.generate(WritingTaskType.COMPOSE, List.of(), null, null);

		assertThat(generated.promptText()).contains("Viết một đoạn văn tiếng Anh");
	}

	@Test
	void everyFallbackTaskStillCarriesAVietnameseInstruction() {
		when(aiContentClient.completeJson(
				anyString(), anyString(), anyDouble(), anyInt(), eq(LlmWritingPracticeGenerator.LlmPayload.class)))
				.thenThrow(new AiContentException("boom"));

		// Project rule: every practice item must show the learner its requirement in Vietnamese, so
		// the offline fallback must not degrade into an English-only prompt.
		assertThat(generator.generate(WritingTaskType.COMPOSE, List.of(), null, null).promptText())
				.contains("Viết");
		assertThat(generator.generate(WritingTaskType.TRANSLATE_VI_EN, List.of(), null, null).promptText())
				.startsWith("Dịch đoạn văn sau sang tiếng Anh:");
		assertThat(generator.generate(WritingTaskType.TRANSLATE_EN_VI, List.of(), null, null).promptText())
				.startsWith("Dịch đoạn văn sau sang tiếng Việt:");
	}

	@Test
	void fallsBackToADefaultTopicWhenTheModelOmitsOne() {
		stubLlm("""
				{"promptText": "Viết ...", "referenceAnswer": "r"}""");

		GeneratedWritingPractice generated = generator.generate(WritingTaskType.COMPOSE, List.of(), "B1", null);

		assertThat(generated.topic()).isEqualTo("B1 writing task");
	}

	private void stubLlm(String json) {
		when(aiContentClient.completeJson(
				anyString(), anyString(), anyDouble(), anyInt(), eq(LlmWritingPracticeGenerator.LlmPayload.class)))
				.thenAnswer(invocation ->
						new ObjectMapper().readValue(json, LlmWritingPracticeGenerator.LlmPayload.class));
	}
}
