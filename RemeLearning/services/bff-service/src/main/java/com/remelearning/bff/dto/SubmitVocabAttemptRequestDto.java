package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

@Data
public class SubmitVocabAttemptRequestDto {
	private String userId;
	private Long practiceItemId;
	private List<String> answers;
}
