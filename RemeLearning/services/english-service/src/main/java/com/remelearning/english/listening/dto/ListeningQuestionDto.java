package com.remelearning.english.listening.dto;

import com.remelearning.english.listening.domain.ListeningQuestionType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Practice-time question shape. Carries {@code answer} + {@code explanation} so the client can grade
 * MCQ/KEYWORD questions locally for instant feedback (the authoritative score still comes from the
 * submit endpoint). {@code answer} is intentionally null for {@code OPEN} questions - those are
 * graded by an LLM server-side ({@code OpenAnswerGrader}) and cannot be checked on the client.
 */
@Getter
@Builder
public class ListeningQuestionDto {
	private int index;
	private String prompt;
	private ListeningQuestionType type;
	private List<String> options;
	/** Correct answer for local grading (MCQ/KEYWORD only; null for OPEN). Mirrors {@code ListeningQuestionScoring}. */
	private String answer;
	private String explanation;
}
