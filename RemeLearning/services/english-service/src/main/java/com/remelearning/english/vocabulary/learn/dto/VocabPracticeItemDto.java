package com.remelearning.english.vocabulary.learn.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
public class VocabPracticeItemDto {
	private Long practiceItemId;
	private String level;
	private String examType;
	private String topic;
	private List<String> targetWords;
	private List<VocabQuestionDto> questions;
	private Instant createdAt;
}
