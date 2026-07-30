package com.remelearning.english.writing.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One AI-generated writing/translation prompt (row in {@code writing_practice_items}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingPracticeItem {
	private Long id;
	private String userId;
	private WritingTaskType taskType;
	private String level;
	private String examType;
	private String topic;
	/** The task brief (COMPOSE) or source passage to translate (TRANSLATE_*), with its Vietnamese instruction. */
	private String promptText;
	private String sourceLang;
	private String targetLang;
	/** Model answer / reference translation - used only for grading, never returned before submission. */
	private String referenceAnswer;
	/** JSON array of the weak-point labels this prompt targets. */
	private String targetLabelsJson;
	private Instant createdAt;
}
