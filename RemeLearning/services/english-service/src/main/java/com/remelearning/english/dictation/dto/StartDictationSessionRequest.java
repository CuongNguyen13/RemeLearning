package com.remelearning.english.dictation.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * Payload for POST /api/v1/dictation/sessions/{userId} - requests a batch of library clips to
 * dictate, filtered by any subset of the taxonomy facets (all null = any clip). Clips are picked
 * at random within the filter so repeated sessions vary.
 */
@Data
public class StartDictationSessionRequest {

	/** Macro skill, e.g. "Listening"; null means any. */
	private String skill;

	/** CEFR level, e.g. "B1"; null means any. */
	private String level;

	/** Topic, e.g. "At home"; null means any. */
	private String topic;

	/** Exam/practice type, e.g. "TOEIC"/"IELTS"; null means any. */
	private String examType;

	@Min(1)
	private int count = 5;
}
