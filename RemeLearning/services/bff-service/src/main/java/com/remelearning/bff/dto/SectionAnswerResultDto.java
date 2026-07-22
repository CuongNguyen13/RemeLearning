package com.remelearning.bff.dto;

import lombok.Data;

@Data
public class SectionAnswerResultDto {
	private boolean correct;
	private String correctAnswer;
	private double score;
	private boolean completed;
	private SectionCardDto nextCard;
	private SectionProgressDto progress;
}
