package com.remelearning.english.writing.grading;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.common.constants.LearningCategories;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.writing.domain.WritingCriteriaScores;
import com.remelearning.english.writing.domain.WritingErrorItem;
import com.remelearning.english.writing.domain.WritingTaskType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The only {@link WritingGrader}: one Gemini call scores the submission per criterion and returns a
 * list of labelled mistakes. Getting a reusable {@code label} + {@code category} on every error is
 * the whole reason grading is LLM-backed rather than rule-based - those two fields are what let a
 * writing mistake merge into the learner's existing grammar/vocabulary weak points.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmWritingGrader implements WritingGrader {

	private static final String SYSTEM_PROMPT = """
			You are a strict but constructive English writing examiner marking ONE submission by a
			Vietnamese learner. You're given the task type, the prompt the learner answered, a
			reference answer (may be absent), and the learner's own text.

			Score each criterion from 0.0 to 1.0:
			- "grammar": grammatical accuracy.
			- "vocabulary": word choice, collocation, range.
			- "coherence": organisation and cohesion between sentences.
			- For TRANSLATE_VI_EN / TRANSLATE_EN_VI, also "accuracy": how faithfully the meaning of
			  the source passage carried over. Set "taskResponse" to null.
			- For COMPOSE, also "taskResponse": how fully the brief was addressed (topic, required
			  structures, minimum length). Set "accuracy" to null.

			Then list EVERY mistake worth teaching, as objects with:
			- "wrong": the incorrect span, quoted verbatim from the learner's own text.
			- "corrected": that same span, fixed.
			- "label": a SHORT, REUSABLE name for the underlying weakness, not a description of this
			  one sentence. Use canonical grammar-rule names ("past perfect", "subject-verb
			  agreement", "article usage") or vocabulary-issue names ("collocation: make/do",
			  "word form: advise/advice"). The same underlying mistake must always get the same label.
			- "category": EXACTLY "grammar" or "vocabulary". Nothing else.
			- "explanationVi": why it is wrong, in Vietnamese.
			- "severity": "minor" or "major".

			Respond with STRICTLY a raw JSON object (no markdown fences, no commentary):
			{"criteria": {"grammar": 0.0, "vocabulary": 0.0, "coherence": 0.0, "accuracy": 0.0 or null, "taskResponse": 0.0 or null},
			 "correctedText": "the learner's text, rewritten correctly",
			 "feedbackVi": "2-4 sentences of overall remarks in Vietnamese",
			 "errors": [{"wrong": "...", "corrected": "...", "label": "...", "category": "grammar", "explanationVi": "...", "severity": "major"}]}
			An empty "errors" array is correct when the submission genuinely has no mistakes.""";

	private final AiContentClient aiContentClient;

	// One LLM call per submission. Any failure degrades to a neutral 0.5 across the board with no
	// errors: the learner still gets their attempt recorded and sees why grading was unavailable,
	// and an empty error list means nothing bogus is written into their weak points.
	@Override
	public WritingGrade grade(
			WritingTaskType taskType, String promptText, String referenceAnswer, String submittedText) {
		try {
			String userPrompt = """
					Task type: %s
					Prompt the learner answered:
					%s

					Reference answer:
					%s

					Learner's submission:
					%s""".formatted(
					taskType.name(),
					promptText,
					referenceAnswer == null || referenceAnswer.isBlank() ? "(none provided)" : referenceAnswer,
					submittedText);
			LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.2, 2400, LlmPayload.class);
			return new WritingGrade(
					toCriteria(taskType, payload.criteria),
					payload.correctedText,
					toErrors(payload.errors),
					payload.feedbackVi);
		} catch (AiContentException ex) {
			log.warn("LLM writing grading failed for {}, returning a neutral grade", taskType, ex);
			return neutralGrade(taskType);
		}
	}

	// Clamps every score to [0, 1] and keeps only the fourth criterion that applies to this task
	// type, so a model that fills in both (or neither) can't produce a criteria object the UI or the
	// mean-score calculation would misread. A missing score counts as 0 and is logged: silently
	// treating it as "perfect" would inflate the learner's result.
	private WritingCriteriaScores toCriteria(WritingTaskType taskType, LlmCriteria raw) {
		LlmCriteria source = raw == null ? new LlmCriteria() : raw;
		WritingCriteriaScores.WritingCriteriaScoresBuilder builder = WritingCriteriaScores.builder()
				.grammar(clamp("grammar", source.grammar))
				.vocabulary(clamp("vocabulary", source.vocabulary))
				.coherence(clamp("coherence", source.coherence));
		if (taskType.isTranslation()) {
			builder.accuracy(clamp("accuracy", source.accuracy));
		} else {
			builder.taskResponse(clamp("taskResponse", source.taskResponse));
		}
		return builder.build();
	}

	private double clamp(String criterion, Double value) {
		if (value == null) {
			log.warn("LLM writing grade omitted the '{}' criterion, scoring it 0", criterion);
			return 0.0;
		}
		return Math.max(0.0, Math.min(1.0, value));
	}

	// Drops errors the weak-point pipeline could not route anywhere: a blank label has nothing to
	// key on, and a category outside grammar/vocabulary has no owning domain. Both are logged rather
	// than silently swallowed, since they mean the prompt contract was broken.
	private List<WritingErrorItem> toErrors(List<LlmError> raw) {
		List<WritingErrorItem> errors = new ArrayList<>();
		if (raw == null) {
			return errors;
		}
		for (LlmError error : raw) {
			if (error.label == null || error.label.isBlank()) {
				log.warn("Skipping an LLM-reported writing error with no label");
				continue;
			}
			String category = error.category == null ? null : error.category.trim().toLowerCase();
			if (!LearningCategories.GRAMMAR.equals(category) && !LearningCategories.VOCABULARY.equals(category)) {
				log.warn("Skipping writing error '{}' with unroutable category '{}'", error.label, error.category);
				continue;
			}
			errors.add(WritingErrorItem.builder()
					.wrong(error.wrong)
					.corrected(error.corrected)
					.label(error.label.trim())
					.category(category)
					.explanationVi(error.explanationVi)
					.severity(error.severity)
					.build());
		}
		return errors;
	}

	private WritingGrade neutralGrade(WritingTaskType taskType) {
		WritingCriteriaScores.WritingCriteriaScoresBuilder builder = WritingCriteriaScores.builder()
				.grammar(0.5).vocabulary(0.5).coherence(0.5);
		if (taskType.isTranslation()) {
			builder.accuracy(0.5);
		} else {
			builder.taskResponse(0.5);
		}
		return new WritingGrade(builder.build(), null, List.of(),
				"Hiện chưa chấm được bài của bạn do lỗi kết nối tới AI. Bài làm đã được lưu, bạn hãy thử nộp lại sau.");
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmPayload {
		private LlmCriteria criteria;
		@JsonAlias("corrected_text")
		private String correctedText;
		@JsonAlias("feedback_vi")
		private String feedbackVi;
		private List<LlmError> errors;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmCriteria {
		private Double grammar;
		private Double vocabulary;
		private Double coherence;
		private Double accuracy;
		@JsonAlias("task_response")
		private Double taskResponse;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	static class LlmError {
		private String wrong;
		private String corrected;
		private String label;
		private String category;
		@JsonAlias("explanation_vi")
		private String explanationVi;
		private String severity;
	}
}
