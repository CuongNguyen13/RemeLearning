package com.remelearning.english.writing.library.generator;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.writing.domain.WritingTaskType;
import com.remelearning.english.writing.library.domain.WritingLibraryPrompt;
import com.remelearning.english.writing.library.domain.WritingLibraryTopic;
import com.remelearning.english.writing.library.domain.WritingTaxonomy;
import com.remelearning.english.writing.library.mapper.WritingLibraryPromptMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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
@RequiredArgsConstructor
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
			- TRANSLATE_VI_EN: "promptText" is a short VIETNAMESE passage (3-5 sentences) fitting the
			  topic. "referenceAnswer" is your reference ENGLISH translation.
			- TRANSLATE_EN_VI: "promptText" is a short ENGLISH passage (3-5 sentences) fitting the topic.
			  "referenceAnswer" is your reference VIETNAMESE translation.

			For both TRANSLATE_* types, "promptText" MUST begin with a one-line Vietnamese instruction
			(e.g. "Dịch đoạn văn sau sang tiếng Anh:") followed by a blank line and then the passage.

			Respond with STRICTLY a raw JSON object (no markdown fences, no commentary):
			{"promptText": "...", "referenceAnswer": "...", "minWords": 80,
			 "explanation": "gợi ý ngắn bằng tiếng Việt về cách làm bài này"}""";

	private final AiContentClient aiContentClient;
	private final WritingLibraryPromptMapper promptMapper;

	// Generates, then immediately persists, so the prompt becomes a stable part of the topic's chain
	// (the learner can leave and come back to the same task, and it can be re-graded/reviewed later).
	@Override
	public WritingLibraryPrompt generatePrompt(WritingLibraryTopic topic, WritingTaskType taskType) {
		LlmPayload payload = callLlm(topic, taskType);
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

	// One LLM call; any failure degrades to a template built from the topic's own name/description,
	// which is always enough to pose a usable task.
	private LlmPayload callLlm(WritingLibraryTopic topic, WritingTaskType taskType) {
		try {
			String userPrompt = """
					Axis: %s
					Topic name: %s
					Topic description: %s
					Level: %s
					Task type: %s""".formatted(
					topic.getTaxonomy(),
					topic.getName(),
					topic.getDescription() == null ? "(none)" : topic.getDescription(),
					topic.getLevel(),
					taskType.name());
			LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.6, 1400, LlmPayload.class);
			if (payload.promptText == null || payload.promptText.isBlank()) {
				throw new AiContentException("LLM returned a library writing prompt with no prompt text");
			}
			payload.promptText = payload.promptText.trim();
			return payload;
		} catch (AiContentException ex) {
			log.warn("LLM writing library prompt generation failed for topic {} ({}), falling back to a template",
					topic.getId(), taskType, ex);
			return fallback(topic, taskType);
		}
	}

	// Template built from the topic itself, so even offline the learner gets a task that still points
	// at the right axis and still states its requirement in Vietnamese.
	private LlmPayload fallback(WritingLibraryTopic topic, WritingTaskType taskType) {
		LlmPayload payload = new LlmPayload();
		payload.minWords = DEFAULT_MIN_WORDS;
		payload.explanation = "Hãy bám sát chủ đề \"%s\" và đọc lại bài sau khi viết.".formatted(topic.getName());
		payload.promptText = switch (taskType) {
			case COMPOSE -> """
					Viết một đoạn văn tiếng Anh (tối thiểu %d từ) về chủ đề "%s".

					Yêu cầu: %s""".formatted(DEFAULT_MIN_WORDS, topic.getName(), fallbackRequirement(topic));
			case TRANSLATE_VI_EN -> """
					Dịch đoạn văn sau sang tiếng Anh:

					Chủ đề "%s" là một phần quan trọng trong việc học tiếng Anh. Tôi đã dành nhiều thời \
					gian để luyện tập nó. Bây giờ tôi thấy mình tiến bộ hơn trước.""".formatted(topic.getName());
			case TRANSLATE_EN_VI -> """
					Dịch đoạn văn sau sang tiếng Việt:

					Learning about "%s" takes regular practice. I have spent a lot of time on it, and I \
					have made steady progress since I started.""".formatted(topic.getName());
		};
		payload.referenceAnswer = null;
		return payload;
	}

	// What the fallback brief asks the learner to do, phrased per axis so an offline COMPOSE task
	// still reflects why this topic exists.
	private String fallbackRequirement(WritingLibraryTopic topic) {
		WritingTaxonomy taxonomy;
		try {
			taxonomy = WritingTaxonomy.fromCode(topic.getTaxonomy());
		} catch (IllegalArgumentException ex) {
			log.warn("Unknown writing library taxonomy '{}' on topic {}, using a generic requirement",
					topic.getTaxonomy(), topic.getId());
			return "viết mạch lạc, đúng ngữ pháp.";
		}
		return switch (taxonomy) {
			case GRAMMAR -> "dùng cấu trúc \"%s\" ít nhất ba lần trong bài.".formatted(topic.getName());
			case GENRE -> "viết đúng thể loại \"%s\", đúng văn phong và đối tượng người đọc.".formatted(topic.getName());
			case VOCAB_THEME -> "dùng ít nhất năm từ/cụm từ thuộc chủ đề \"%s\".".formatted(topic.getName());
		};
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
