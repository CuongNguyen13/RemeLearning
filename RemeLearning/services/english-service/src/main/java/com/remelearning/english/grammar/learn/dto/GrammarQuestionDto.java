package com.remelearning.english.grammar.learn.dto;

import com.remelearning.english.grammar.learn.domain.GrammarQuestionType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Practice-time question shape. Now carries the correct {@code answer} + {@code translation} so the
 * client can grade each answer locally for instant feedback (the authoritative score still comes
 * from the submit endpoint). Mirrors {@code GrammarAttemptScorer}/{@code ExactMatchScorer}.
 */
@Getter
@Builder
public class GrammarQuestionDto {
	private int index;
	private String prompt;
	private GrammarQuestionType type;
	private List<String> options;
	/** Correct answer for local grading (mirrors {@code GrammarAttemptScorer}). */
	private String answer;
	private String translation;
	/** Vietnamese literal translation of {@code answer} (the correct sentence). */
	private String translationVi;
}
