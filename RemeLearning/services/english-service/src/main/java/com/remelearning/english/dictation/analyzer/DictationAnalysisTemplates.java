package com.remelearning.english.dictation.analyzer;

import java.util.ArrayList;
import java.util.List;

/**
 * Static, no-cost fallback suggestions/practice sentences used by {@link RuleBasedDictationAnalyzer}
 * and whenever {@link LlmDictationAnalyzer} can't reach or parse the LLM.
 */
final class DictationAnalysisTemplates {

	private DictationAnalysisTemplates() {
	}

	// Vietnamese study suggestions; when nothing was missed, praise + a keep-going nudge.
	static List<String> suggestionsFor(List<String> missedWords) {
		if (missedWords == null || missedWords.isEmpty()) {
			return List.of("Tuyệt vời! Bạn nghe và chép chính xác. Hãy thử một đoạn khó hơn.");
		}
		String joined = String.join(", ", missedWords);
		return List.of(
				"Bạn nghe sót/nhầm các từ: " + joined + ". Nghe lại đoạn audio và tập trung vào những từ này.",
				"Luyện nghe từng từ khó rồi chép lại nhiều lần cho đến khi quen âm.",
				"Dùng mục \"Luyện nghe với AI\" để nghe lại các từ này trong câu mới.");
	}

	// One simple practice sentence per missed word, so the learner re-hears it in context.
	static List<String> practiceSentencesFor(List<String> missedWords) {
		List<String> sentences = new ArrayList<>();
		if (missedWords != null) {
			for (String word : missedWords) {
				sentences.add("Listen carefully and write the word \"" + word + "\".");
			}
		}
		if (sentences.isEmpty()) {
			sentences.add("Listen carefully and write the sentence you hear.");
		}
		return sentences;
	}
}
