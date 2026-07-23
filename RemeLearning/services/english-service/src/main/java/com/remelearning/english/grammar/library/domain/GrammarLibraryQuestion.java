package com.remelearning.english.grammar.library.domain;

import com.remelearning.english.grammar.learn.domain.GrammarQuestionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row in {@code grammar_library_questions} — part of the reusable 8-10 question pool
 * generated once per topic alongside its {@link GrammarLibraryContent}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarLibraryQuestion {
	private Long id;
	private Long topicId;
	private GrammarQuestionType questionType;
	private String prompt;
	/** JSON array of options; null unless {@code questionType == MCQ}. */
	private String optionsJson;
	private String answer;
	private String explanationVi;
	/** Plain Vietnamese translation of {@code answer} (the sentence's meaning, not the grammar rule). */
	private String translationVi;
	private Instant createdAt;
}
