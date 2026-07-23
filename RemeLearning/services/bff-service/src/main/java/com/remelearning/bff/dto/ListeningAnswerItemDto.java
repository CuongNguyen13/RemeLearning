package com.remelearning.bff.dto;

import lombok.Data;

/** One submitted answer for a listening-library section question. */
@Data
public class ListeningAnswerItemDto {
	private Long questionId;
	private String selectedOption;
}
