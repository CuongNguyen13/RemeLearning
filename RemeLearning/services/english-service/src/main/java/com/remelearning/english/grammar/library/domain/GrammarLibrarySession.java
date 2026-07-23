package com.remelearning.english.grammar.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row in {@code grammar_library_sessions} — a learner's in-progress or finished practice run
 * against one topic. {@code questionsJson} is the serialized {@code List<GrammarLibrarySessionQuestion>}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarLibrarySession {
	private Long id;
	private String userId;
	private Long topicId;
	private GrammarSessionType sessionType;
	private String questionsJson;
	private GrammarSessionStatus status;
	private int correctCount;
	private int totalCount;
	private Instant startedAt;
	private Instant completedAt;
}
