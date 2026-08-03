package com.remelearning.recommendation.exercise;

import java.util.List;
import java.util.Map;

/**
 * Static, category-agnostic exercise lists - the pluralized equivalent of ai-service's
 * {@code rule_based_analyzer.py}'s {@code _RECOMMENDATION_TEMPLATES}. Backs
 * {@link RuleBasedExerciseGenerator}, the generator active unless
 * {@code recommendation.exercise-generator.mode=llm}; kept as a plain static helper (not a Spring
 * bean) so it never registers a second {@link ExerciseGenerator} bean. Not a failure fallback:
 * {@link LlmExerciseGenerator} propagates its own failures instead of borrowing these.
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
					"Luyện đọc to 5 câu có chứa \"%s\" trước gương trong 5 phút."),
			"listening", List.of(
					"Nghe lại một đoạn hội thoại có \"%s\" và ghi ra những gì bạn nghe được.",
					"Luyện nghe chép chính tả (dictation) một đoạn ngắn chứa \"%s\".",
					"Nghe rồi nhắc lại (shadowing) 3 câu có \"%s\" để quen với cách nói tự nhiên."),
			"writing", List.of(
					"Viết một đoạn văn ngắn buộc phải dùng \"%s\" ít nhất ba lần.",
					"Dịch 5 câu tiếng Việt sang tiếng Anh sao cho câu nào cũng cần đến \"%s\".",
					"Viết lại những câu bạn từng sai về \"%s\" cho đúng, rồi tự so với bản sửa của AI."));

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
