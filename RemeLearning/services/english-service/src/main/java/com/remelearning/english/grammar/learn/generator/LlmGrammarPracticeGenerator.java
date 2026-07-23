package com.remelearning.english.grammar.learn.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.grammar.learn.domain.GrammarQuestionItem;
import com.remelearning.english.grammar.learn.domain.GrammarQuestionType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The only {@link GrammarPracticeGenerator}: this skill is AI-only (Phần 2 of the "Học &amp;
 * Luyện tập với AI" plan) - there's no switchable rule-based mode, only a static-template fallback
 * for when the LLM call/parse fails, so generating a practice set never breaks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmGrammarPracticeGenerator implements GrammarPracticeGenerator {

	private static final String SYSTEM_PROMPT = """
			You are an English-grammar coach building a short practice set for a learner. You're given a
			list of target grammar rules to drill (possibly empty - if empty, pick suitable rules yourself
			for the requested level) plus an optional CEFR level and exam style. Produce ONE exercise item
			per target rule (or, if no target rules were given, 6-8 items on rules you choose), each of a
			randomly varied type. Respond with STRICTLY a raw JSON object (no markdown fences, no
			commentary) of the shape:
			{"topic": "...", "items": [{"targetRule": "...", "type": "ERROR_CORRECTION|FILL_TENSE|TRANSFORM|MCQ",
			"prompt": "...", "options": ["...", "..."] or null, "answer": "...", "translation": "...",
			"translationVi": "..."}]}
			- "topic": a short label for the whole set (e.g. "Past tenses").
			- "ERROR_CORRECTION": "prompt" is one English sentence containing a grammar mistake; "options"
			  is null; "answer" is the corrected sentence.
			- "FILL_TENSE": "prompt" is a sentence with the verb to use in brackets (e.g. "She (go) to
			  school yesterday."); "options" is null; "answer" is the complete sentence with the verb in
			  the correct form.
			- "TRANSFORM": "prompt" is the original English sentence followed by the transformation
			  instruction written IN VIETNAMESE inside parentheses - the English sentence stays English,
			  only the instruction is Vietnamese (e.g. "The chef cooked the meal. (Viết lại câu ở thể bị
			  động)"); "options" is null; "answer" is the transformed sentence.
			- "MCQ": "prompt" is a sentence with a blank; "options" is 4 candidate structures (one
			  correct); "answer" is the correct option text (must match one option exactly).
			- "translation": a short Vietnamese explanation of why the answer is correct / which rule
			  applies.
			- "translationVi": the plain Vietnamese translation of the full correct sentence in "answer"
			  (not a grammar explanation - an actual meaning translation, e.g. answer "The climate is
			  changing." -> translationVi "Khí hậu đang thay đổi.").
			- Keep sentences natural, 6-16 words, appropriate for the requested level/exam style.""";

	private final AiContentClient aiContentClient;

	@Override
	public GeneratedGrammarPractice generate(List<String> targetRules, String level, String examType) {
		try {
			String userPrompt = "Target grammar rules: %s\nLevel: %s\nExam style: %s".formatted(
					targetRules.isEmpty() ? "(none - please choose suitable rules yourself)" : targetRules,
					level == null ? "(unspecified)" : level,
					examType == null ? "(unspecified)" : examType);
			LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.6, 1200, LlmPayload.class);
			GeneratedGrammarPractice result = toResult(payload);
			if (result.items().isEmpty()) {
				throw new AiContentException("LLM returned no grammar items");
			}
			return result;
		} catch (AiContentException ex) {
			log.warn("LLM grammar practice generation failed, falling back to templates", ex);
			return fallback(targetRules, level);
		}
	}

	private GeneratedGrammarPractice toResult(LlmPayload payload) {
		List<GrammarQuestionItem> items = new ArrayList<>();
		for (LlmItem raw : nullToEmpty(payload.items)) {
			items.add(GrammarQuestionItem.builder()
					.targetRule(raw.targetRule)
					.type(parseType(raw.type))
					.prompt(raw.prompt)
					.options(raw.options)
					.answer(raw.answer)
					.translation(raw.translation)
					.translationVi(raw.translationVi)
					.build());
		}
		return new GeneratedGrammarPractice(payload.topic, items);
	}

	private GrammarQuestionType parseType(String raw) {
		if (raw == null) {
			return GrammarQuestionType.MCQ;
		}
		try {
			return GrammarQuestionType.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			log.warn("Unrecognized grammar question type '{}', defaulting to MCQ", raw);
			return GrammarQuestionType.MCQ;
		}
	}

	// One templated MCQ-shaped item per target rule (or one generic "practice" item if none were
	// given), so generating a set never fails even with the LLM unreachable.
	private GeneratedGrammarPractice fallback(List<String> targetRules, String level) {
		List<String> rules = targetRules.isEmpty() ? List.of("present simple") : targetRules;
		List<GrammarQuestionItem> items = rules.stream()
				.map(rule -> GrammarQuestionItem.builder()
						.targetRule(rule)
						.type(GrammarQuestionType.ERROR_CORRECTION)
						.prompt("Write one correct sentence practicing: " + rule + ".")
						.answer(rule)
						.translation(null)
						.build())
				.toList();
		return new GeneratedGrammarPractice(level == null ? "Grammar practice" : level + " grammar practice", items);
	}

	private static <T> List<T> nullToEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class LlmPayload {
		private String topic;
		private List<LlmItem> items;
	}

	@Getter
	@Setter
	@JsonIgnoreProperties(ignoreUnknown = true)
	private static class LlmItem {
		private String targetRule;
		private String type;
		private String prompt;
		private List<String> options;
		private String answer;
		private String translation;
		private String translationVi;
	}
}
