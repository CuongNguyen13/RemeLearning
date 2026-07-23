package com.remelearning.english.grammar.learn.scoring;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GrammarScoreResult {
	private double accuracy;
	/** Per-question correctness, aligned by index to the item's questions. */
	private List<Boolean> perQuestionCorrect;
}
