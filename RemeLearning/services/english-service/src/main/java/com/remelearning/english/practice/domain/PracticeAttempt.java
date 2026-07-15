package com.remelearning.english.practice.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One graded answer submitted while a learner redoes an exercise; an audit-log row only. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PracticeAttempt {
	private Long id;
	private String userId;
	private String itemId;
	private String category;
	private String label;
	private boolean correct;
	private Instant attemptedAt;
}
