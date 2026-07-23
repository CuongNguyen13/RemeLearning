package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

@Data
public class SubmitListeningAttemptRequestDto {
	private String userId;
	private Long practiceItemId;
	private List<String> answers;
}
