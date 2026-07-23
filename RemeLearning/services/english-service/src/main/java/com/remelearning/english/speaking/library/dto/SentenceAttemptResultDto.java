package com.remelearning.english.speaking.library.dto;

/** Scoring result for one recorded sentence attempt - does not itself affect topic gating (see {@code finishSection}). */
public class SentenceAttemptResultDto {
	private Long sentenceId;
	private double phonemeScore;
	private double wordScore;
	private boolean passed;
	private String transcript;

	public SentenceAttemptResultDto(Long sentenceId, double phonemeScore, double wordScore, boolean passed, String transcript) {
		this.sentenceId = sentenceId;
		this.phonemeScore = phonemeScore;
		this.wordScore = wordScore;
		this.passed = passed;
		this.transcript = transcript;
	}

	public Long getSentenceId() { return sentenceId; }
	public double getPhonemeScore() { return phonemeScore; }
	public double getWordScore() { return wordScore; }
	public boolean isPassed() { return passed; }
	public String getTranscript() { return transcript; }
}
