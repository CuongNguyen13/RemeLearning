package com.remelearning.english.dictation.analyzer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Static, no-cost fallback error classification/advice/practice sentences used by
 * {@link RuleBasedDictationAnalyzer} and whenever {@link LlmDictationAnalyzer} can't reach or parse
 * the LLM.
 */
final class DictationAnalysisTemplates {

	// Common English function words: when misheard, it's almost always their unstressed/weak spoken
	// form (connected speech), not an unknown word - classified as PHONOLOGY rather than LEXICON.
	private static final Set<String> FUNCTION_WORDS = Set.of(
			"a", "an", "the", "of", "to", "for", "and", "or", "but", "is", "was", "are", "were",
			"do", "does", "did", "have", "has", "had", "can", "could", "will", "would", "should",
			"at", "in", "on", "as", "that", "than", "them", "there", "your", "you");

	private DictationAnalysisTemplates() {
	}

	// Heuristic root-cause classification for one missed/substituted word pair (no LLM available):
	// a differing s/ed/ing suffix suggests a grammar/morphology slip; a common function word
	// suggests the learner missed its weak spoken form; anything else defaults to vocabulary.
	static DictationErrorCategory classify(String expected, String actual) {
		if (expected != null && suffixDiffers(expected, actual)) {
			return DictationErrorCategory.GRAMMAR;
		}
		if (expected != null && FUNCTION_WORDS.contains(expected.toLowerCase())) {
			return DictationErrorCategory.PHONOLOGY;
		}
		return DictationErrorCategory.LEXICON;
	}

	// True when stripping a trailing s/ed/ing from either word makes them match (e.g. "finish" vs
	// "finished"), meaning the learner likely heard the stem but missed the inflection.
	private static boolean suffixDiffers(String expected, String actual) {
		if (actual == null || expected.equalsIgnoreCase(actual)) {
			return false;
		}
		return stripInflection(expected).equalsIgnoreCase(stripInflection(actual));
	}

	private static String stripInflection(String word) {
		String lower = word.toLowerCase();
		if (lower.endsWith("ing") && lower.length() > 4) {
			return lower.substring(0, lower.length() - 3);
		}
		if (lower.endsWith("ed") && lower.length() > 3) {
			return lower.substring(0, lower.length() - 2);
		}
		if (lower.endsWith("s") && lower.length() > 2) {
			return lower.substring(0, lower.length() - 1);
		}
		return lower;
	}

	// One static root-cause summary per category, only ever attached when that category has misses.
	static String summaryFor(DictationErrorCategory category) {
		return switch (category) {
			case LEXICON -> "Một số từ/cụm từ này còn lạ hoặc dễ nhầm âm - nghe lại nhiều lần để quen mặt chữ và âm thanh.";
			case GRAMMAR -> "Bạn nghe sót các hình thái từ (thêm 's'/'ed'/'ing') - đây thường là lỗi ngữ pháp/thì, không phải không nghe được từ gốc.";
			case PHONOLOGY -> "Đây là các từ chức năng thường được nói ở dạng âm yếu/lướt âm khi nói tự nhiên - luyện nghe connected speech sẽ giúp bắt được chúng.";
		};
	}

	// One static actionable-advice line per category, only added when that category has misses.
	static String adviceFor(DictationErrorCategory category) {
		return switch (category) {
			case LEXICON -> "Tra và học lại các từ/cụm từ bị nhầm trong mục \"Luyện nghe với AI\".";
			case GRAMMAR -> "Ôn lại quy tắc chia động từ/danh từ (thì, số ít-số nhiều) rồi luyện chép lại các câu chứa chúng.";
			case PHONOLOGY -> "Luyện shadowing các cụm chứa từ chức năng này để quen với cách nói nối/lướt âm tự nhiên.";
		};
	}

	// Vietnamese study advice; when nothing was missed, praise + a keep-going nudge.
	static List<String> praiseAdvice() {
		return List.of("Tuyệt vời! Bạn nghe và chép chính xác. Hãy thử một đoạn khó hơn.");
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
