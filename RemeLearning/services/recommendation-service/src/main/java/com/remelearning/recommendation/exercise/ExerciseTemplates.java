package com.remelearning.recommendation.exercise;

import java.util.List;
import java.util.Map;

/**
 * Static, category-agnostic fallback exercise lists - the pluralized equivalent of ai-service's
 * {@code rule_based_analyzer.py}'s {@code _RECOMMENDATION_TEMPLATES}. Kept as a plain static
 * helper (not a Spring bean) so both {@link RuleBasedExerciseGenerator} and
 * {@link LlmExerciseGenerator}'s failure-fallback path can reuse the same content without ever
 * registering two beans for {@link ExerciseGenerator} at once.
 */
final class ExerciseTemplates {

	private static final Map<String, List<String>> TEMPLATES = Map.of(
			"grammar", List.of(
					"Viết 5 câu ví dụ đúng ngữ pháp có dùng \"%s\".",
					"Làm 10 câu bài tập trắc nghiệm/điền khuyết về \"%s\".",
					"Thử dùng \"%s\" khi nói chuyện hoặc ghi âm bản thân trong 5 phút."),
			"vocabulary", List.of(
					"Đặt 5 câu mới với từ/cụm từ \"%s\".",
					"Làm flashcard cho \"%s\" và ôn lại theo lịch spaced-repetition trong tuần này.",
					"Dùng \"%s\" trong một đoạn hội thoại ngắn (nói hoặc viết) với bạn học/AI."),
			"pronunciation", List.of(
					"Nghe mẫu phát âm chuẩn của \"%s\" 3 lần rồi nhại lại (shadowing).",
					"Ghi âm bản thân đọc \"%s\" và so sánh với bản mẫu.",
					"Luyện đọc to 5 câu có chứa \"%s\" trước gương trong 5 phút."));

	private static final List<String> DEFAULT_TEMPLATE = List.of("Ôn lại nội dung: \"%s\".");

	private ExerciseTemplates() {
	}

	// Returns a fixed set of generic exercises for the given category, with label filled in; a null
	// or unrecognized category (e.g. a malformed Kafka payload from ai-service) falls back to the
	// generic default template rather than throwing - Map.of()'s immutable map rejects a null key
	// on lookup, so the null check must happen before calling getOrDefault.
	static List<String> defaultsFor(String category, String label) {
		List<String> templates = category == null ? DEFAULT_TEMPLATE : TEMPLATES.getOrDefault(category, DEFAULT_TEMPLATE);
		return templates.stream().map(template -> template.formatted(label)).toList();
	}
}
