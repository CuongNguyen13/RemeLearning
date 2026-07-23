package com.remelearning.english.speaking.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row in {@code speaking_library_sentences} — a sample sentence (with its IPA transcription and
 * synthesized sample audio) belonging to a {@link SpeakingLibrarySection}, for a learner to practice
 * reading aloud. The speaking-library equivalent of {@code ListeningLibraryQuestion}, but scored by
 * pronunciation rather than by a multiple-choice answer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakingLibrarySentence {
	private Long id;
	private Long sectionId;
	private String sentenceText;
	/** IPA transcription of {@link #sentenceText}, shown to the learner as a pronunciation guide. */
	private String ipa;
	/** S3 storage key for the Supertonic-synthesized sample audio; null if not yet generated. */
	private String sampleAudioStorageKey;
	private Instant createdAt;
}
