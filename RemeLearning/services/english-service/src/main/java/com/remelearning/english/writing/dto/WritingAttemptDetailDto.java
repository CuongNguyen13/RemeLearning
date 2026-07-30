package com.remelearning.english.writing.dto;

import com.remelearning.english.writing.domain.WritingCriteriaScores;
import com.remelearning.english.writing.domain.WritingErrorItem;
import com.remelearning.english.writing.domain.WritingTaskType;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/** Full detail of one past attempt, for the "xem lại" dialog. */
@Data
@Builder
public class WritingAttemptDetailDto {
	private Long attemptId;
	private Long practiceItemId;
	private WritingTaskType taskType;
	private String level;
	private String examType;
	private String topic;
	private String promptText;
	private String submittedText;
	private String correctedText;
	private double overallScore;
	private WritingCriteriaScores criteria;
	private List<WritingErrorItem> errors;
	private String feedback;
	private String referenceAnswer;
	private Instant attemptedAt;
}
