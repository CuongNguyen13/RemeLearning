package com.remelearning.english.vocabulary.learn.generator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.remelearning.english.learn.common.AiContentClient;
import com.remelearning.english.learn.common.AiContentException;
import com.remelearning.english.vocabulary.learn.domain.VocabQuestionItem;
import com.remelearning.english.vocabulary.learn.domain.VocabQuestionType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The only {@link VocabPracticeGenerator}: this skill is AI-only (Phần 2 of the "Học &amp; Luyện
 * tập với AI" plan) - there's no switchable rule-based mode, only a static-template fallback for
 * when the LLM call/parse fails, so generating a practice set never breaks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LlmVocabPracticeGenerator implements VocabPracticeGenerator {

	private static final String SYSTEM_PROMPT = """
			You are an English-vocabulary coach building a short practice set for a learner. You're given
			a list of target words to drill (possibly empty - if empty, pick suitable words yourself for
			the requested level) plus an optional CEFR level and exam style. Produce ONE exercise item per
			target word (or, if no target words were given, 6-8 items on words you choose), each of a
			randomly varied type. Respond with STRICTLY a raw JSON object (no markdown fences, no
			commentary) of the shape:
			{"topic": "...", "items": [{"targetWord": "...", "type": "CLOZE|MCQ|MATCHING", "prompt": "...",
			"options": ["...", "..."] or null, "answer": "...", "translation": "..."}]}
			- "topic": a short label for the whole set (e.g. "Travel vocabulary").
			- "CLOZE": "prompt" is an English sentence using the target word replaced with "____";
			  "options" is null; "answer" is the missing word.
			- "MCQ": "prompt" is an English sentence with a blank; "options" is 4 English words (one being
			  the target word); "answer" is the target word.
			- "MATCHING": "prompt" is just the target word; "options" is 4 short English meaning/definition
			  candidates (one correct); "answer" is the correct meaning text (must match one option exactly).
			- "translation": the target word's Vietnamese meaning.
			- Keep sentences natural, 6-16 words, appropriate for the requested level/exam style.""";

	private final AiContentClient aiContentClient;

	@Override
	public GeneratedVocabPractice generate(List<String> targetWords, String level, String examType) {
		try {
			String userPrompt = "Target words: %s\nLevel: %s\nExam style: %s".formatted(
					targetWords.isEmpty() ? "(none - please choose suitable words yourself)" : targetWords,
					level == null ? "(unspecified)" : level,
					examType == null ? "(unspecified)" : examType);
			LlmPayload payload = aiContentClient.completeJson(SYSTEM_PROMPT, userPrompt, 0.6, 1200, LlmPayload.class);
			GeneratedVocabPractice result = toResult(payload);
			if (result.items().isEmpty()) {
				throw new AiContentException("LLM returned no vocabulary items");
			}
			return result;
		} catch (AiContentException ex) {
			log.warn("LLM vocabulary practice generation failed, falling back to templates", ex);
			return fallback(targetWords, level);
		}
	}

	private GeneratedVocabPractice toResult(LlmPayload payload) {
		List<VocabQuestionItem> items = new ArrayList<>();
		for (LlmItem raw : nullToEmpty(payload.items)) {
			items.add(VocabQuestionItem.builder()
					.targetWord(raw.targetWord)
					.type(parseType(raw.type))
					.prompt(raw.prompt)
					.options(raw.options)
					.answer(raw.answer)
					.translation(raw.translation)
					.build());
		}
		return new GeneratedVocabPractice(payload.topic, items);
	}

	private VocabQuestionType parseType(String raw) {
		if (raw == null) {
			return VocabQuestionType.CLOZE;
		}
		try {
			return VocabQuestionType.valueOf(raw.trim().toUpperCase());
		} catch (IllegalArgumentException ex) {
			log.warn("Unrecognized vocab question type '{}', defaulting to CLOZE", raw);
			return VocabQuestionType.CLOZE;
		}
	}

	// One templated cloze item per target word (or one generic "practice" item if none were given),
	// so generating a set never fails even with the LLM unreachable.
	private GeneratedVocabPractice fallback(List<String> targetWords, String level) {
		List<String> words = targetWords.isEmpty() ? List.of("practice") : targetWords;
		List<VocabQuestionItem> items = words.stream()
				.map(word -> VocabQuestionItem.builder()
						.targetWord(word)
						.type(VocabQuestionType.CLOZE)
						.prompt("Please use the word \"____\" in a sentence of your own (target: " + word + ").")
						.answer(word)
						.translation(null)
						.build())
				.toList();
		return new GeneratedVocabPractice(level == null ? "Vocabulary practice" : level + " vocabulary practice", items);
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
		private String targetWord;
		private String type;
		private String prompt;
		private List<String> options;
		private String answer;
		private String translation;
	}
}
