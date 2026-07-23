package com.remelearning.english.grammar.learn.dto;

import lombok.Data;

import java.util.List;

/**
 * Request to generate one AI grammar practice set. When {@code focusItems} is empty/omitted, the
 * service falls back to the learner's own top grammar weak points, then to a generic
 * level-appropriate set if the learner has none yet.
 */
@Data
public class GenerateGrammarPracticeRequest {
	private String level;
	private String examType;
	private List<String> focusItems;
}
