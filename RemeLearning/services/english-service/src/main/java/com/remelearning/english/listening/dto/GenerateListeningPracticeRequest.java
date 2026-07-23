package com.remelearning.english.listening.dto;

import lombok.Data;

import java.util.List;

/**
 * Request to generate one AI listening-practice passage. When {@code focusItems} is empty/
 * omitted, the service falls back to the learner's own recently-missed dictation/listening
 * keywords, then to a generic level-appropriate passage if there's no history yet.
 */
@Data
public class GenerateListeningPracticeRequest {
	private String level;
	private String examType;
	private String translationLang;
	private List<String> focusItems;
}
