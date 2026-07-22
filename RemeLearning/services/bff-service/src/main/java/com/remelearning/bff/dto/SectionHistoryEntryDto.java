package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class SectionHistoryEntryDto {
	private Long sectionAttemptId;
	private String topicName;
	private double accuracy;
	private int wordsCount;
	private Instant completedAt;
}
