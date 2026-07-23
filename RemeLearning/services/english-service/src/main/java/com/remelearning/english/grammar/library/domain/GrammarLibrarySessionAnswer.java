package com.remelearning.english.grammar.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One graded answer inside a session (row in {@code grammar_library_session_answers}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarLibrarySessionAnswer {
	private Long id;
	private Long sessionId;
	private String questionRef;
	private String submittedAnswer;
	private boolean correct;
	private Instant answeredAt;
}
