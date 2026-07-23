package com.remelearning.english.listening.library.dto;

import java.util.List;

/** A learner's submitted answers for one section's question pool. */
public class SubmitListeningAnswersRequest {
	private List<AnswerItem> answers;

	public List<AnswerItem> getAnswers() { return answers; }
	public void setAnswers(List<AnswerItem> answers) { this.answers = answers; }

	public record AnswerItem(Long questionId, String selectedOption) {
	}
}
