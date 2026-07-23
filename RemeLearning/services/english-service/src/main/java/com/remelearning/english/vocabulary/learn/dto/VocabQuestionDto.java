package com.remelearning.english.vocabulary.learn.dto;

import com.remelearning.english.vocabulary.learn.domain.VocabQuestionType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Practice-time question shape. Now carries the correct {@code answer} + {@code translation} so the
 * client can grade each answer locally for instant feedback (the authoritative score still comes
 * from the submit endpoint). This deliberately trades the old "client can't cheat" guarantee for
 * client-side checking - the exact-match rule the client mirrors lives in {@code ExactMatchScorer}.
 */
@Getter
@Builder
public class VocabQuestionDto {
	private int index;
	private String prompt;
	private VocabQuestionType type;
	private List<String> options;
	/** Correct answer for local grading (mirrors {@code VocabAttemptScorer}). */
	private String answer;
	private String translation;
}
