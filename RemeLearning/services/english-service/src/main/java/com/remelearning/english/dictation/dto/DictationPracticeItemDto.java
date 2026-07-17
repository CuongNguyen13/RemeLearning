package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One AI-practice item for the "Luyện nghe với AI" section. {@code audioUrl} is null until the
 * Supertonic audio has been synthesized; the learner dictates the (hidden) sentence by ear.
 */
@Getter
@Builder
public class DictationPracticeItemDto {
	private Long practiceItemId;
	private String audioUrl;
}
