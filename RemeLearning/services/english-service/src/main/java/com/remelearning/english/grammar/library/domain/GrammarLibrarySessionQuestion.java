package com.remelearning.english.grammar.library.domain;

import com.remelearning.english.grammar.learn.domain.GrammarQuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * One question snapshot inside {@link GrammarLibrarySession#getQuestionsJson()} — a full copy of
 * the question content (not just a foreign-key reference), since a {@code RETRY} session's
 * questions are freshly AI-generated and never persisted to {@link GrammarLibraryQuestion}, so
 * both session types need to carry their own content inline. {@code questionRef} is unique within
 * the owning session only (e.g. {@code "q-42"} for a pool question, {@code "r-0"} for a retry
 * question), used to match a submitted answer back to its question.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarLibrarySessionQuestion {
	private String questionRef;
	private GrammarQuestionType type;
	private String prompt;
	private List<String> options;
	private String answer;
	private String explanationVi;
	private String translationVi;
}
