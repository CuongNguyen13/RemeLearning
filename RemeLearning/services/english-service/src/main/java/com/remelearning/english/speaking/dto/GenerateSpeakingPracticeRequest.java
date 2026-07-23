package com.remelearning.english.speaking.dto;

import lombok.Data;

import java.util.List;

/**
 * Request to generate one AI speaking-practice sentence/passage. When {@code focusItems} is
 * empty/omitted, the service falls back to the learner's own top pronunciation weak points, then
 * to a generic level-appropriate sentence if the learner has none yet.
 */
@Data
public class GenerateSpeakingPracticeRequest {
	private String level;
	private String examType;
	private List<String> focusItems;
}
