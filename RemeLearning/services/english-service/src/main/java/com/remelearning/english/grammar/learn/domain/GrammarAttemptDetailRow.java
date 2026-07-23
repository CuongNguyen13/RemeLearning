package com.remelearning.english.grammar.learn.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Full detail for one past attempt: its answers/score plus the owning item's questions/taxonomy. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarAttemptDetailRow {
	private Long attemptId;
	private String level;
	private String examType;
	private String topic;
	private String itemsJson;
	private String answersJson;
	private double score;
	private Instant createdAt;
}
