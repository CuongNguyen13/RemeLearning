package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Full detail for one AI-practice item, returned only when the learner opens it to practice
 * sentence-by-sentence (GET /api/v1/dictation/ai-practice/items/{practiceItemId}/detail) - mirrors
 * {@link DictationClipDetailDto} so the same sentence-mode runner works for both the library and the
 * "Luyện nghe với AI" section. {@code sentences} never carry AI-aligned timestamps (the passage's
 * audio is one merged file with no per-sentence timing), so the client falls back to its own
 * word-count-share estimate the same way it does for a library clip not yet aligned.
 */
@Getter
@Builder
public class DictationPracticeItemDetailDto {
	private Long practiceItemId;
	private String audioUrl;
	private String scriptText;
	private String level;
	private String examType;
	private String topic;
	private List<DictationSentenceDto> sentences;
}
