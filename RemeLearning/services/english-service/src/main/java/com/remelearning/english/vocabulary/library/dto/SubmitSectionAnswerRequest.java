package com.remelearning.english.vocabulary.library.dto;

import lombok.Data;

/** The learner's answer for the current card; omit/blank for an INTRO card (acknowledge-only). */
@Data
public class SubmitSectionAnswerRequest {
	private String submittedAnswer;
}
