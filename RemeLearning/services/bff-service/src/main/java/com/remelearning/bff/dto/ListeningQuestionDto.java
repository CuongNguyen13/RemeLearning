package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Practice-time question shape (carries answer/explanation for client-side grading; answer null for OPEN), proxied from english-service. */
@Data
public class ListeningQuestionDto {
	private int index;
	private String prompt;
	private String type;
	private List<String> options;
	private String answer;
	private String explanation;
}
