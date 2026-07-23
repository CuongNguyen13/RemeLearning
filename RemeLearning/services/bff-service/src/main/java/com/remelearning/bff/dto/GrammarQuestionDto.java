package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Practice-time question shape (now carries answer/translation for client-side grading), proxied from english-service. */
@Data
public class GrammarQuestionDto {
	private int index;
	private String prompt;
	private String type;
	private List<String> options;
	private String answer;
	private String translation;
	private String translationVi;
}
