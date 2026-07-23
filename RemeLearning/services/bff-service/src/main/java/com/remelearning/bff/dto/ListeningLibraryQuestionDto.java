package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Learner-facing view of one listening-library section question: no correct option/explanation, so answers aren't leaked. */
@Data
public class ListeningLibraryQuestionDto {
	private Long questionId;
	private String questionText;
	private List<String> options;
}
