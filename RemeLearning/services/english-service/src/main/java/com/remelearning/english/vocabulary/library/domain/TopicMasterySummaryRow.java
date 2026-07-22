package com.remelearning.english.vocabulary.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One row of the per-user, per-topic mastery aggregate query (mapper result row, not a table). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicMasterySummaryRow {
	private Long topicId;
	private int wordCount;
	private int masteredCount;
}
