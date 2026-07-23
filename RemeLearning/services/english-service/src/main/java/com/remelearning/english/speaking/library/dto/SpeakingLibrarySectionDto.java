package com.remelearning.english.speaking.library.dto;

import java.util.List;

/** A section ready to practice: its pool of sample sentences, each with IPA + a fetchable sample-audio URL. */
public class SpeakingLibrarySectionDto {
	private Long sectionId;
	private List<SentenceView> sentences;

	public SpeakingLibrarySectionDto(Long sectionId, List<SentenceView> sentences) {
		this.sectionId = sectionId;
		this.sentences = sentences;
	}

	public Long getSectionId() { return sectionId; }
	public List<SentenceView> getSentences() { return sentences; }

	/** Learner-facing view of a sentence: text, IPA and the fetchable sample-audio URL (null if not yet generated). */
	public record SentenceView(Long sentenceId, String sentenceText, String ipa, String sampleAudioUrl) {
	}
}
