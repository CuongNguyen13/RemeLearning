package com.remelearning.english.writing.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One learner's gating state for one topic (row in {@code writing_topic_progress}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingTopicProgress {
	private Long id;
	private String userId;
	private Long topicId;
	private WritingTopicStatus status;
	private Instant unlockedAt;
	private Instant passedAt;
	private Instant updatedAt;
}
