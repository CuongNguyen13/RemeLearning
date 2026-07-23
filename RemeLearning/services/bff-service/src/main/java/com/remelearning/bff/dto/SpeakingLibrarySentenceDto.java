package com.remelearning.bff.dto;

import lombok.Data;

/** Learner-facing view of one speaking-library section sentence: text, IPA and the fetchable sample-audio URL (null if not yet generated). */
@Data
public class SpeakingLibrarySentenceDto {
	private Long sentenceId;
	private String sentenceText;
	private String ipa;
	private String sampleAudioUrl;
}
