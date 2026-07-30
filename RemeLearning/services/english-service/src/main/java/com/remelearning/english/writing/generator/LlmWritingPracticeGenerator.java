package com.remelearning.english.writing.generator;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.writing.domain.WritingTaskType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The only {@link WritingPracticeGenerator}: one Gemini call produces the prompt plus its reference
 * answer, built around the learner's weakest grammar/vocabulary labels so the act of writing forces
 * them to use exactly what they keep getting wrong. A static-template fallback covers any LLM/parse
 * failure so generating a prompt never breaks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmWritingPracticeGenerator implements WritingPracticeGenerator {

	private static final String SYSTEM_PROMPT = """
			You are an English writing coach preparing ONE practice task for a Vietnamese learner.
			You're given a task type, a list of grammar/vocabulary weak-point labels to target
			(possibly empty - if empty, pick a suitable everyday topic yourself for the requested
			level), an optional CEFR level and an optional exam style.

			Build the task according to the task type:
			- COMPOSE: "promptText" is a writing brief written IN VIETNAMESE telling the learner what
			  to write in English. It must state a minimum word count and explicitly list the target
			  structures/words they have to use. "referenceAnswer" is a model answer IN ENGLISH.
			- TRANSLATE_VI_EN: "promptText" is a short VIETNAMESE passage (3-5 sentences) that
			  naturally requires the target structures/words when translated. "referenceAnswer" is
			  your reference ENGLISH translation.
			- TRANSLATE_EN_VI: "promptText" is a short ENGLISH passage (3-5 sentences) naturally
			  using the target structures/words. "referenceAnswer" is your reference VIETNAMESE
			  translation.

			For both TRANSLATE_* types, "promptText" MUST begin with a one-line Vietnamese
			instruction (e.g. "Dịch đoạn văn sau sang tiếng Anh:") followed by a blank line and then
			the passage itself.

			Respond with STRICTLY a raw JSON object (no markdown fences, no commentary) of the shape:
			{"topic": "short label, max 60 chars", "promptText": "...", "referenceAnswer": "..."}""";

	private final AiContentClient aiContentClient;

	// One LLM call per generated task; any failure (unreachable LLM, non-JSON reply, missing field)
	// degrades to the static template below rather than propagating, since the caller has already
	// committed to returning the learner a task.
	@Override
	public GeneratedWritingPractice generate(
			WritingTaskType taskType, List<String> targetLabels, String level, String examType) {
		try {
			String userPrompt = "Task type: %s\nTarget labels: %s\nLevel: %s\nExam style: %s".formatted(
					taskType.name(),
					targetLabels == null || targetLabels.isEmpty()
							? "(none - please choose a suitable topic yourself)" : targetLabels,
					level == null ? "(unspecified)" : level,
					examType == null ? "(unspecified)" : examType);
			LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.6, 1400, LlmPayload.class);
			if (isBlank(payload.promptText)) {
				throw new AiContentException("LLM returned a writing task with no prompt text");
			}
			return new GeneratedWritingPractice(
					isBlank(payload.topic) ? defaultTopic(taskType, level) : payload.topic.trim(),
					payload.promptText.trim(),
					payload.referenceAnswer == null ? null : payload.referenceAnswer.trim());
		} catch (AiContentException ex) {
			log.warn("LLM writing practice generation failed for {}, falling back to a template", taskType, ex);
			return fallback(taskType, level);
		}
	}

	// A fixed, level-agnostic task per mode so generation never fails even with the LLM unreachable.
	// Each keeps the same contract as the normal path: a Vietnamese instruction in prompt_text and a
	// usable reference answer for the grader.
	private GeneratedWritingPractice fallback(WritingTaskType taskType, String level) {
		return switch (taskType) {
			case COMPOSE -> new GeneratedWritingPractice(
					defaultTopic(taskType, level),
					"""
							Viết một đoạn văn tiếng Anh (tối thiểu 80 từ) kể về một ngày làm việc hoặc học tập \
							đáng nhớ của bạn.

							Yêu cầu: dùng ít nhất một câu ở thì quá khứ đơn và một câu ở thì hiện tại hoàn thành.""",
					"Last Monday was a memorable day for me. I finished a project that I had worked on for weeks. "
							+ "Since then, I have felt much more confident about my own skills.");
			case TRANSLATE_VI_EN -> new GeneratedWritingPractice(
					defaultTopic(taskType, level),
					"""
							Dịch đoạn văn sau sang tiếng Anh:

							Tôi đã sống ở Hà Nội trong năm năm. Trước khi chuyển đến đây, tôi đã làm việc ở \
							một thành phố nhỏ. Bây giờ tôi đã quen với nhịp sống nhanh của thành phố này.""",
					"I have lived in Hanoi for five years. Before I moved here, I had worked in a small city. "
							+ "Now I have got used to the fast pace of this city.");
			case TRANSLATE_EN_VI -> new GeneratedWritingPractice(
					defaultTopic(taskType, level),
					"""
							Dịch đoạn văn sau sang tiếng Việt:

							She had already left the office when I arrived. I have been trying to reach her all \
							morning, but she has not answered any of my calls yet.""",
					"Cô ấy đã rời khỏi văn phòng trước khi tôi đến. Tôi đã cố gắng liên lạc với cô ấy suốt cả "
							+ "buổi sáng, nhưng cô ấy vẫn chưa trả lời cuộc gọi nào của tôi.");
		};
	}

	private String defaultTopic(WritingTaskType taskType, String level) {
		String prefix = level == null ? "" : level + " ";
		return switch (taskType) {
			case COMPOSE -> prefix + "writing task";
			case TRANSLATE_VI_EN -> prefix + "translation (VI → EN)";
			case TRANSLATE_EN_VI -> prefix + "translation (EN → VI)";
		};
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	// AiContentClient parses with a default (camelCase) ObjectMapper, so the prompt asks for
	// camelCase keys; the aliases keep parsing working if the model reverts to snake_case anyway,
	// which would otherwise silently produce a null promptText and force the fallback.
	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmPayload {
		private String topic;
		@JsonAlias("prompt_text")
		private String promptText;
		@JsonAlias("reference_answer")
		private String referenceAnswer;
	}
}
