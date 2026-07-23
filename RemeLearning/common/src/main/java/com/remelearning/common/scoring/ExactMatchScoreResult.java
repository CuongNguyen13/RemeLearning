package com.remelearning.common.scoring;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Result of an exact-normalized-match scoring pass: overall accuracy plus per-question
 * correctness, aligned by index to the questions passed in.
 */
@Getter
@Builder
public class ExactMatchScoreResult {
	private double accuracy;
	private List<Boolean> perQuestionCorrect;
}
