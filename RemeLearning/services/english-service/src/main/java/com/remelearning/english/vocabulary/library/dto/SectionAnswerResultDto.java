package com.remelearning.english.vocabulary.library.dto;

import lombok.Builder;
import lombok.Getter;

/** Result of grading one Section card (or acknowledging an INTRO); {@code nextCard} is null once {@code completed}. */
@Getter
@Builder
public class SectionAnswerResultDto {
	private boolean correct;
	private String correctAnswer;
	private double score;
	private boolean completed;
	private SectionCardDto nextCard;
	private SectionProgressDto progress;
}
