package com.remelearning.english.writing.generator;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.common.constants.ExamTypes;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.writing.domain.WritingTaskType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The only {@link WritingPracticeGenerator}: one Gemini call produces the prompt plus its reference
 * answer, built around the learner's weakest grammar/vocabulary labels so the act of writing forces
 * them to use exactly what they keep getting wrong. No static-template fallback: any LLM/parse
 * failure propagates as {@link AiContentException} so the learner is never handed a canned task.
 */
@Slf4j
@Component
public class LlmWritingPracticeGenerator implements WritingPracticeGenerator {

	private static final String SYSTEM_PROMPT = """
			You are an English writing coach preparing ONE practice task for a Vietnamese learner.
			You're given a task type, a list of grammar/vocabulary weak-point labels to target
			(possibly empty), an optional CEFR level, the exam style being prepared for, and - decided
			by the caller, NOT by you - the exact number of sentences the source passage must have, a
			suggested subject area, the register to write in, and (for COMPOSE) which kind of text to
			ask for.

			Build the task according to the task type:
			- COMPOSE: "promptText" is a writing brief written IN VIETNAMESE telling the learner what
			  to write in English, in the requested text format. It must state a minimum word count and
			  explicitly list the target structures/words they have to use. "referenceAnswer" is a model
			  answer IN ENGLISH.
			- TRANSLATE_VI_EN: "promptText" is a VIETNAMESE passage of EXACTLY the requested number of
			  sentences, which naturally requires the target structures/words when translated.
			  "referenceAnswer" is your reference ENGLISH translation.
			- TRANSLATE_EN_VI: "promptText" is an ENGLISH passage of EXACTLY the requested number of
			  sentences, naturally using the target structures/words. "referenceAnswer" is your
			  reference VIETNAMESE translation.

			THE PASSAGE MUST BE ONE CONTINUOUS TEXT ABOUT ONE SINGLE SITUATION - never a list of
			unrelated example sentences that merely happen to contain the target structures. Before
			writing a single word, fix ONE scene: one narrator/main subject, one place, one time frame,
			one thing that happens. Then write the sentences as consecutive steps of that same scene:
			- The first sentence opens the situation; every later sentence must visibly continue it -
			  a cause, a consequence, a detail, a reaction - and the last sentence must close it off.
			- Keep the SAME people, place and time frame from the first sentence to the last. Do not
			  bring in a new person, place or event that the rest of the passage never mentions again.
			- Chain the sentences explicitly: refer back with pronouns/possessives to what was already
			  named, and use connectives (vì thế, sau đó, nhưng, nhờ vậy, cuối cùng / so, then, but,
			  because of that, in the end ...) instead of starting each sentence from nothing.
			- Fit the target structures INTO that one situation. If a target structure cannot be used
			  naturally inside it, drop that structure - NEVER bend or break the storyline to squeeze
			  one in, and never let a sentence exist only to demonstrate a structure.
			- Re-read the finished passage as a whole before answering. If any sentence could be
			  deleted without the reader feeling something is missing, or if the passage reads like a
			  grammar drill rather than one short story/message, rewrite it.
			- "topic" must name that one situation (not the grammar being practised), so it reads as
			  the title of the passage.

			Obey the requested sentence count exactly - it is what makes each generated task a
			different length instead of always the same shape. The situation ALWAYS comes from the
			suggested subject area; the target labels only decide which structures/words have to appear
			inside it, they never turn the passage into a set of drills on their own. Match the
			requested register.

			For both TRANSLATE_* types, "promptText" MUST begin with a one-line Vietnamese
			instruction (e.g. "Dịch đoạn văn sau sang tiếng Anh:") followed by a blank line and then
			the passage itself.

			Respond with STRICTLY a raw JSON object (no markdown fences, no commentary) of the shape:
			{"topic": "short label, max 60 chars", "promptText": "...", "referenceAnswer": "..."}""";

	private final AiContentClient aiContentClient;
	private final int maxOutputTokens;

	public LlmWritingPracticeGenerator(
			AiContentClient aiContentClient,
			@Value("${writing.practice.max-output-tokens:8000}") int maxOutputTokens) {
		this.aiContentClient = aiContentClient;
		this.maxOutputTokens = maxOutputTokens;
	}

	// One LLM call per generated task; any failure (unreachable LLM, non-JSON reply, missing field)
	// degrades to the static template below rather than propagating, since the caller has already
	// committed to returning the learner a task.
	//
	// The exam style is resolved into a WritingExamProfile first, and the concrete choices it implies
	// (sentence count, subject area, register, COMPOSE text format) are decided HERE and handed to the
	// model as instructions. Letting the model interpret "TOEIC" itself produced passages that were
	// all the same length and roughly the same register regardless of the exam.
	//
	// The system prompt spends most of its length on coherence because the target labels pull the model
	// the other way: asked for N sentences that use N structures, it wrote N unrelated sentences (a
	// late-night habit, then a brother playing games, then turning off the light) instead of one scene.
	// Hence the explicit "fix one scene first, drop a structure rather than break it" instructions.
	@Override
	public GeneratedWritingPractice generate(
			WritingTaskType taskType, List<String> targetLabels, String level, String examType) {
		WritingExamProfile profile = WritingExamProfile.fromExamType(examType);
		int sentenceCount = profile.randomSentenceCount();
		String userPrompt = """
				Task type: %s
				Target labels: %s
				Level: %s
				Exam style: %s
				Sentences the passage must have: %d
				Suggested subject area: %s
				Register: %s%s""".formatted(
				taskType.name(),
				targetLabels == null || targetLabels.isEmpty()
						? "(none - build the task around the suggested subject area instead)" : targetLabels,
				level == null ? "(unspecified)" : level,
				ExamTypes.normalize(examType) == null ? "General (no exam in mind)" : ExamTypes.normalize(examType),
				sentenceCount,
				profile.randomTopic(),
				profile.registerHint(),
				taskType == WritingTaskType.COMPOSE
						? "%nText format to ask for: %s".formatted(profile.randomComposeFormat()) : "");
		// Higher than the 1400 used by the sibling library-content generator because reasoning-routed
		// Zen models (e.g. big-pickle) can burn a large part of the budget on hidden chain-of-thought
		// even with reasoning disabled - see OpenAiZenLlmClient's javadoc - leaving too little would
		// starve the actual JSON content.
		LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.6, maxOutputTokens, LlmPayload.class);
		if (isBlank(payload.promptText)) {
			log.warn("LLM returned a writing task with no prompt text for {}", taskType);
			throw new AiContentException("LLM returned a writing task with no prompt text");
		}
		return new GeneratedWritingPractice(
				isBlank(payload.topic) ? defaultTopic(taskType, level) : payload.topic.trim(),
				payload.promptText.trim(),
				payload.referenceAnswer == null ? null : payload.referenceAnswer.trim());
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
