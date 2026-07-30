package com.remelearning.english.writing.library.dto;

import com.remelearning.english.writing.domain.WritingTaskType;
import lombok.Builder;
import lombok.Data;

/**
 * One library prompt as the client sees it. Like {@code WritingPracticeItemDto} this deliberately has
 * no reference-answer field, so the model answer cannot reach the browser before submission.
 */
@Data
@Builder
public class WritingLibraryPromptDto {
	private Long promptId;
	private Long topicId;
	private String topicName;
	private WritingTaskType taskType;
	private String promptText;
	private String sourceLang;
	private String targetLang;
	private Integer minWords;
	/** Position in the topic's chain, 1-based, for a "bài 3/7" style indicator. */
	private int position;
	private int targetPromptCount;
}
