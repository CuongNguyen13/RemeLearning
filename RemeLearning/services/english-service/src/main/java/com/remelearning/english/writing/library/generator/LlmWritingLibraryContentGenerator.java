package com.remelearning.english.writing.library.generator;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.common.constants.ExamTypes;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.writing.domain.WritingTaskType;
import com.remelearning.english.writing.generator.WritingExamProfile;
import com.remelearning.english.writing.library.domain.WritingLibraryPrompt;
import com.remelearning.english.writing.library.domain.WritingLibraryTopic;
import com.remelearning.english.writing.library.mapper.WritingLibraryPromptMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The only {@link WritingLibraryContentGenerator}: one Gemini call per prompt, generated the first
 * time a learner reaches that point in a topic's chain (the catalogue ships topics, not content -
 * same lazy model as {@code LlmListeningLibraryGenerator}).
 *
 * <p>The axis matters to the prompt: a {@code grammar} topic must force its structure to be used, a
 * {@code genre} topic must produce a task in that text type, and a {@code vocab_theme} topic must
 * push the theme's vocabulary. One system prompt covers all three by telling the model which axis it
 * is generating for.
 */
@Slf4j
@Component
public class LlmWritingLibraryContentGenerator implements WritingLibraryContentGenerator {

	private static final int DEFAULT_MIN_WORDS = 80;

	private static final String SYSTEM_PROMPT = """
			You are an English writing coach authoring ONE practice task for a Vietnamese learner, for a
			fixed library topic. You're given the topic's axis, its name and description, its level, and
			the task type.

			What the axis means for the task you write:
			- grammar: the task MUST make the learner use that grammar structure naturally and
			  repeatedly. Say so explicitly in the brief.
			- genre: the task MUST be a real piece of writing of that text type (email, report, IELTS
			  task, ...), with a realistic situation and audience.
			- vocab_theme: the task MUST push the learner to use the theme's typical vocabulary.

			What the task type means:
			- COMPOSE: "promptText" is a brief written IN VIETNAMESE telling the learner what to write in
			  English, stating a minimum word count and what must be used. "referenceAnswer" is a model
			  answer IN ENGLISH.
			- TRANSLATE_VI_EN: "promptText" is a VIETNAMESE passage of EXACTLY the requested number of
			  sentences, fitting the topic. "referenceAnswer" is your reference ENGLISH translation.
			- TRANSLATE_EN_VI: "promptText" is an ENGLISH passage of EXACTLY the requested number of
			  sentences, fitting the topic. "referenceAnswer" is your reference VIETNAMESE translation.

			Obey the requested sentence count and register exactly - they come from the exam style the
			learner is preparing for, and are what make a TOEIC task read differently from an IELTS one.

			For both TRANSLATE_* types, "promptText" MUST begin with a one-line Vietnamese instruction
			(e.g. "Dịch đoạn văn sau sang tiếng Anh:") followed by a blank line and then the passage.

			Respond with STRICTLY a raw JSON object (no markdown fences, no commentary):
			{"promptText": "...", "referenceAnswer": "...", "minWords": 80,
			 "explanation": "gợi ý ngắn bằng tiếng Việt về cách làm bài này"}""";

	private final AiContentClient aiContentClient;
	private final WritingLibraryPromptMapper promptMapper;
	private final int maxOutputTokens;

	public LlmWritingLibraryContentGenerator(
			AiContentClient aiContentClient,
			WritingLibraryPromptMapper promptMapper,
			@Value("${writing.library.max-output-tokens:1400}") int maxOutputTokens) {
		this.aiContentClient = aiContentClient;
		this.promptMapper = promptMapper;
		this.maxOutputTokens = maxOutputTokens;
	}

	// Generates, then immediately persists, so the prompt becomes a stable part of the topic's chain
	// (the learner can leave and come back to the same task, and it can be re-graded/reviewed later).
	@Override
	public WritingLibraryPrompt generatePrompt(
			WritingLibraryTopic topic, WritingTaskType taskType, String examType) {
		LlmPayload payload = callLlm(topic, taskType, examType);
		WritingLibraryPrompt prompt = WritingLibraryPrompt.builder()
				.topicId(topic.getId())
				.taskType(taskType)
				.promptText(payload.promptText)
				.referenceAnswer(payload.referenceAnswer)
				.minWords(payload.minWords == null || payload.minWords <= 0 ? DEFAULT_MIN_WORDS : payload.minWords)
				.explanation(payload.explanation)
				.build();
		promptMapper.insert(prompt);
		return prompt;
	}

	// One LLM call, no fallback: a failed or prompt-less generation propagates as
	// AiContentException so the caller reports the error instead of persisting a canned task.
	//
	// The exam style resolves to a WritingExamProfile whose sentence count is drawn fresh per call, so
	// two learners on the same library topic - or the same learner coming back to it - get passages of
	// different lengths in the register their exam actually calls for.
	private LlmPayload callLlm(WritingLibraryTopic topic, WritingTaskType taskType, String examType) {
		WritingExamProfile profile = WritingExamProfile.fromExamType(examType);
		String userPrompt = """
				Axis: %s
				Topic name: %s
				Topic description: %s
				Level: %s
				Task type: %s
				Exam style: %s
				Sentences the passage must have: %d
				Register: %s""".formatted(
				topic.getTaxonomy(),
				topic.getName(),
				topic.getDescription() == null ? "(none)" : topic.getDescription(),
				topic.getLevel(),
				taskType.name(),
				ExamTypes.normalize(examType) == null ? "General (no exam in mind)" : ExamTypes.normalize(examType),
				profile.randomSentenceCount(),
				profile.registerHint());
		LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.6, maxOutputTokens, LlmPayload.class);
		if (payload.promptText == null || payload.promptText.isBlank()) {
			log.warn("LLM returned a library writing prompt with no prompt text for topic {} ({})", topic.getId(), taskType);
			throw new AiContentException("LLM returned a library writing prompt with no prompt text");
		}
		payload.promptText = payload.promptText.trim();
		return payload;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmPayload {
		@JsonAlias("prompt_text")
		private String promptText;
		@JsonAlias("reference_answer")
		private String referenceAnswer;
		@JsonAlias("min_words")
		private Integer minWords;
		private String explanation;
	}
}
