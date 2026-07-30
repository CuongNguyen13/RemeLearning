package com.remelearning.english.writing.library.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.writing.domain.WritingTaskType;
import com.remelearning.english.writing.library.domain.WritingLibraryPrompt;
import com.remelearning.english.writing.library.domain.WritingLibraryTopic;
import com.remelearning.english.writing.library.mapper.WritingLibraryPromptMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmWritingLibraryContentGeneratorTest {

	private final AiContentClient aiContentClient = mock(AiContentClient.class);
	private final WritingLibraryPromptMapper promptMapper = mock(WritingLibraryPromptMapper.class);
	private final LlmWritingLibraryContentGenerator generator =
			new LlmWritingLibraryContentGenerator(aiContentClient, promptMapper);

	@Test
	void generatesAndPersistsThePromptSoItBecomesPartOfTheTopicChain() {
		stubLlm("""
				{"promptText": "Viết một email công việc...", "referenceAnswer": "Dear Sir,...",
				 "minWords": 120, "explanation": "Chú ý văn phong trang trọng."}""");

		WritingLibraryPrompt prompt = generator.generatePrompt(
				topic(10L, "genre", "Email công việc"), WritingTaskType.COMPOSE, null);

		assertThat(prompt.getPromptText()).isEqualTo("Viết một email công việc...");
		assertThat(prompt.getReferenceAnswer()).isEqualTo("Dear Sir,...");
		assertThat(prompt.getMinWords()).isEqualTo(120);
		assertThat(prompt.getTopicId()).isEqualTo(10L);
		verify(promptMapper).insert(prompt);
	}

	@Test
	void tellsTheModelWhichAxisItIsGeneratingForSoTheTaskMatchesTheTopic() {
		stubLlm("""
				{"promptText": "p", "referenceAnswer": "r", "minWords": 80}""");

		generator.generatePrompt(topic(10L, "vocab_theme", "Travel"), WritingTaskType.TRANSLATE_VI_EN, null);

		verify(aiContentClient).completeJson(
				anyString(), contains("Axis: vocab_theme"), anyDouble(), anyInt(),
				eq(LlmWritingLibraryContentGenerator.LlmPayload.class));
		verify(aiContentClient).completeJson(
				anyString(), contains("TRANSLATE_VI_EN"), anyDouble(), anyInt(),
				eq(LlmWritingLibraryContentGenerator.LlmPayload.class));
	}

	@Test
	void defaultsAnAbsentOrNonsensicalMinWordCount() {
		stubLlm("""
				{"promptText": "p", "referenceAnswer": "r", "minWords": 0}""");

		assertThat(generator.generatePrompt(topic(10L, "grammar", "Past Perfect"), WritingTaskType.COMPOSE, null)
				.getMinWords()).isEqualTo(80);
	}

	@Test
	void fallsBackToATopicDerivedTemplateWhenTheLlmFails() {
		when(aiContentClient.completeJson(
				anyString(), anyString(), anyDouble(), anyInt(),
				eq(LlmWritingLibraryContentGenerator.LlmPayload.class)))
				.thenThrow(new AiContentException("boom"));

		WritingLibraryPrompt prompt = generator.generatePrompt(
				topic(10L, "grammar", "Past Perfect"), WritingTaskType.COMPOSE, null);

		// Offline, the task must still name the topic and state its requirement in Vietnamese.
		assertThat(prompt.getPromptText())
				.contains("Past Perfect")
				.contains("dùng cấu trúc");
		verify(promptMapper).insert(prompt);
	}

	@Test
	void theFallbackRequirementReflectsTheAxis() {
		when(aiContentClient.completeJson(
				anyString(), anyString(), anyDouble(), anyInt(),
				eq(LlmWritingLibraryContentGenerator.LlmPayload.class)))
				.thenThrow(new AiContentException("boom"));

		assertThat(generator.generatePrompt(topic(10L, "genre", "Báo cáo ngắn"), WritingTaskType.COMPOSE, null)
				.getPromptText()).contains("đúng thể loại");
		assertThat(generator.generatePrompt(topic(10L, "vocab_theme", "Travel"), WritingTaskType.COMPOSE, null)
				.getPromptText()).contains("từ/cụm từ thuộc chủ đề");
	}

	@Test
	void anUnknownAxisFallsBackToAGenericRequirementInsteadOfThrowing() {
		when(aiContentClient.completeJson(
				anyString(), anyString(), anyDouble(), anyInt(),
				eq(LlmWritingLibraryContentGenerator.LlmPayload.class)))
				.thenThrow(new AiContentException("boom"));

		// A row with a taxonomy this build doesn't know must not break the learner's session.
		assertThat(generator.generatePrompt(topic(10L, "phonics", "Weird"), WritingTaskType.COMPOSE, null)
				.getPromptText()).contains("viết mạch lạc");
	}

	@Test
	void translationFallbacksStillCarryTheirVietnameseInstruction() {
		when(aiContentClient.completeJson(
				anyString(), anyString(), anyDouble(), anyInt(),
				eq(LlmWritingLibraryContentGenerator.LlmPayload.class)))
				.thenThrow(new AiContentException("boom"));

		assertThat(generator.generatePrompt(topic(10L, "grammar", "Past Perfect"), WritingTaskType.TRANSLATE_VI_EN, null)
				.getPromptText()).startsWith("Dịch đoạn văn sau sang tiếng Anh:");
		assertThat(generator.generatePrompt(topic(10L, "grammar", "Past Perfect"), WritingTaskType.TRANSLATE_EN_VI, null)
				.getPromptText()).startsWith("Dịch đoạn văn sau sang tiếng Việt:");
	}

	@Test
	void fallsBackWhenTheModelReturnsNoPromptText() {
		stubLlm("""
				{"promptText": "  ", "referenceAnswer": "r"}""");

		WritingLibraryPrompt prompt = generator.generatePrompt(
				topic(10L, "grammar", "Past Perfect"), WritingTaskType.COMPOSE, null);

		assertThat(prompt.getPromptText()).contains("Past Perfect");
	}

	@Test
	void acceptsSnakeCaseKeysIfTheModelIgnoresTheCamelCaseContract() {
		stubLlm("""
				{"prompt_text": "Viết ...", "reference_answer": "Ref.", "min_words": 100}""");

		ArgumentCaptor<WritingLibraryPrompt> captor = ArgumentCaptor.forClass(WritingLibraryPrompt.class);
		generator.generatePrompt(topic(10L, "grammar", "Past Perfect"), WritingTaskType.COMPOSE, null);

		verify(promptMapper).insert(captor.capture());
		assertThat(captor.getValue().getPromptText()).isEqualTo("Viết ...");
		assertThat(captor.getValue().getMinWords()).isEqualTo(100);
	}

	private WritingLibraryTopic topic(Long id, String taxonomy, String name) {
		return WritingLibraryTopic.builder()
				.id(id).taxonomy(taxonomy).code("code").name(name)
				.description("desc").level("B1").sequenceOrder(1)
				.build();
	}

	private void stubLlm(String json) {
		when(aiContentClient.completeJson(
				anyString(), anyString(), anyDouble(), anyInt(),
				eq(LlmWritingLibraryContentGenerator.LlmPayload.class)))
				.thenAnswer(invocation -> new ObjectMapper()
						.readValue(json, LlmWritingLibraryContentGenerator.LlmPayload.class));
	}
}
