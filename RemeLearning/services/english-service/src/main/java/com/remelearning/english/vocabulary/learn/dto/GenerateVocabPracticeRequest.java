package com.remelearning.english.vocabulary.learn.dto;

import lombok.Data;

import java.util.List;

/**
 * Request to generate one AI vocabulary practice set. When {@code focusItems} is empty/omitted,
 * the service falls back to the learner's own top vocabulary weak points, then to a generic
 * level-appropriate set if the learner has none yet.
 */
@Data
public class GenerateVocabPracticeRequest {
	private String level;
	private String examType;
	private List<String> focusItems;
}
