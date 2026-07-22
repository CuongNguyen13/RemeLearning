package com.remelearning.english.vocabulary.library.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SectionHistoryEntryDto {
	private Long sectionAttemptId;
	private String topicName;
	private double accuracy;
	private int wordsCount;
	private Instant completedAt;
}
