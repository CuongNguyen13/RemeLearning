package com.remelearning.english.listening.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * One AI-generated listening-comprehension question - the unit stored (as a JSON array) inside
 * {@link ListeningPracticeItem#getQuestionsJson()}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeningQuestionItem {
	private ListeningQuestionType type;
	/** Comprehension sub-skill this question drills (e.g. "main-idea", "detail", "attitude",
	 * "keyword") - used as the weak-point label when the learner gets it wrong. */
	private String skill;
	private String prompt;
	/** Options for {@code MCQ}; null for {@code KEYWORD}/{@code OPEN}. */
	private List<String> options;
	/** Correct option (MCQ), expected keyword/phrase (KEYWORD), or model answer (OPEN, used as the LLM grading reference). */
	private String answer;
	/** Vietnamese explanation of the correct answer. */
	private String explanation;
}
