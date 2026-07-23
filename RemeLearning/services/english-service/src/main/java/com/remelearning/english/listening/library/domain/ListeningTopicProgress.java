package com.remelearning.english.listening.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One row in {@code listening_topic_progress} — a learner's progression state against one topic. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeningTopicProgress {
	private Long id;
	private String userId;
	private Long topicId;
	private ListeningTopicStatus status;
	private Instant unlockedAt;
	private Instant passedAt;
	private Instant updatedAt;
}
