package com.remelearning.english.listening.library.dto;

import java.util.List;

/** A section ready to play/answer: passage text, a fetchable audio URL (if generated), and its question pool (answers withheld). */
public class ListeningLibrarySectionDto {
	private Long sectionId;
	private String passageText;
	private String audioUrl;
	private List<QuestionView> questions;

	public ListeningLibrarySectionDto(Long sectionId, String passageText, String audioUrl, List<QuestionView> questions) {
		this.sectionId = sectionId;
		this.passageText = passageText;
		this.audioUrl = audioUrl;
		this.questions = questions;
	}

	public Long getSectionId() { return sectionId; }
	public String getPassageText() { return passageText; }
	public String getAudioUrl() { return audioUrl; }
	public List<QuestionView> getQuestions() { return questions; }

	/** Learner-facing view of a question: no correct option/explanation, so answers aren't leaked. */
	public record QuestionView(Long questionId, String questionText, List<String> options) {
	}
}
