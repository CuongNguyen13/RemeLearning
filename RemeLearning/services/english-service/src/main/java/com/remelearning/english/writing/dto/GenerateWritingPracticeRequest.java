package com.remelearning.english.writing.dto;

import com.remelearning.english.writing.domain.WritingTaskType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * Request to generate one AI writing/translation prompt. When {@code focusItems} is empty/omitted,
 * the service falls back to the learner's own most-forgotten grammar and vocabulary weak points,
 * then to a generic level-appropriate prompt if they have no history yet.
 */
@Data
public class GenerateWritingPracticeRequest {

	@NotNull
	private WritingTaskType taskType;

	private String level;
	private String examType;
	private List<String> focusItems;
}
