package com.remelearning.english.grammar.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One row in {@code grammar_topic_progress} — a learner's progression state against one topic. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarTopicProgress {
	private Long id;
	private String userId;
	private Long topicId;
	private GrammarTopicStatus status;
	private Instant unlockedAt;
	private Instant passedAt;
	private Instant updatedAt;
}
