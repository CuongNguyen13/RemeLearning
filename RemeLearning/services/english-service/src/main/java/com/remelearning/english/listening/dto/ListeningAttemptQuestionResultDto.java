package com.remelearning.english.listening.dto;

import com.remelearning.english.listening.domain.ListeningQuestionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One graded question, revealed after submission (unlike {@link ListeningQuestionDto}, includes
 * the answer). Setters + no-arg constructor exist alongside the builder so it round-trips through
 * Jackson - persisted as JSON in {@code listening_attempts.results} (see
 * {@code ListeningLearnServiceImpl}) rather than recomputed on every read.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeningAttemptQuestionResultDto {
	private int index;
	private String prompt;
	private String yourAnswer;
	private String correctAnswer;
	private boolean correct;
	/** 0..1 partial credit - meaningful for KEYWORD (WER-based) and OPEN (LLM-graded); 0/1 for MCQ. */
	private double subScore;
	private String explanation;
	/**
	 * The source question's shape - added so {@link com.remelearning.english.listening.generator.ListeningMistakeAnalyzer}
	 * can tell OPEN questions (whose {@link #correctAnswer} is a full model-answer sentence, too
	 * diffuse to use as a generator retry keyword) apart from KEYWORD/MCQ (whose {@code correctAnswer}
	 * is already a crisp keyword/option). {@code null} for attempts persisted before this field
	 * existed - treated the same as KEYWORD/MCQ (falls back to the old correctAnswer-only behavior).
	 */
	private ListeningQuestionType type;
}
