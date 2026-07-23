package com.remelearning.english.speaking.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One row in {@code speaking_topic_progress} — a learner's progression state against one topic. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakingTopicProgress {
	private Long id;
	private String userId;
	private Long topicId;
	private SpeakingTopicStatus status;
	private Instant unlockedAt;
	private Instant passedAt;
	private Instant updatedAt;
}
