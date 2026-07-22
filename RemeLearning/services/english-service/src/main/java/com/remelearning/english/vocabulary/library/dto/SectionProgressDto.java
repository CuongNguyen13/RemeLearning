package com.remelearning.english.vocabulary.library.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SectionProgressDto {
	private int totalWords;
	private int wordsMastered;
	private int wordsRemaining;
}
