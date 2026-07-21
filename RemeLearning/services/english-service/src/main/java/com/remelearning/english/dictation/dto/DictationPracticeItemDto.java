package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One AI-practice item for the "Luyện nghe với AI" section. {@code audioUrl} is null until the
 * Supertonic audio has been synthesized; the learner dictates the (hidden) sentence by ear.
 * {@code level}/{@code examType}/{@code topic} are null for items generated without an explicit
 * facet selection (e.g. from a history attempt's mistakes).
 */
@Getter
@Builder
public class DictationPracticeItemDto {
	private Long practiceItemId;
	private String audioUrl;
	private String level;
	private String examType;
	private String topic;
}
