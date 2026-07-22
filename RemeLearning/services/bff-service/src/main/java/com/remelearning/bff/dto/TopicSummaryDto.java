package com.remelearning.bff.dto;

import lombok.Data;

@Data
public class TopicSummaryDto {
	private Long topicId;
	private String code;
	private String name;
	private String description;
	private String level;
	private int wordCount;
	private int masteredCount;
}
