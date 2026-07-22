package com.remelearning.bff.dto;

import lombok.Data;

@Data
public class SectionProgressDto {
	private int totalWords;
	private int wordsMastered;
	private int wordsRemaining;
}
