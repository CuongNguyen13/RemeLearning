package com.remelearning.english.writing.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One past attempt with everything needed to render it again, including the prompt it answered and
 * the reference answer (safe here: the learner has already submitted). Also what the retry action
 * reads, since it needs the old attempt's {@code errorsJson} plus its prompt's level/examType.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingAttemptDetailRow {
	private Long attemptId;
	private Long practiceItemId;
	private WritingTaskType taskType;
	private String level;
	private String examType;
	private String topic;
	private String promptText;
	private String referenceAnswer;
	private String submittedText;
	private String correctedText;
	private double overallScore;
	private String criteriaJson;
	private String errorsJson;
	private String feedback;
	private Instant createdAt;
}
