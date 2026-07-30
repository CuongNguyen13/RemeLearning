package com.remelearning.english.writing.dto;

import com.remelearning.english.writing.domain.WritingTaskType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * One writing/translation prompt as the client sees it. Deliberately has NO {@code referenceAnswer}
 * field - the model answer must not reach the browser before the learner submits, so it is only
 * ever exposed via {@link WritingAttemptResultDto}/{@link WritingAttemptDetailDto}.
 */
@Data
@Builder
public class WritingPracticeItemDto {
	private Long practiceItemId;
	private WritingTaskType taskType;
	private String level;
	private String examType;
	private String topic;
	private String promptText;
	private String sourceLang;
	private String targetLang;
	private List<String> targetLabels;
	private Instant createdAt;
}
