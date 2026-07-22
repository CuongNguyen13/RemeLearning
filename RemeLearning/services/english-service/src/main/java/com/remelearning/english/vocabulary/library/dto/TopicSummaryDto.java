package com.remelearning.english.vocabulary.library.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TopicSummaryDto {
	private Long topicId;
	private String code;
	private String name;
	private String description;
	private String level;
	private int wordCount;
	private int masteredCount;
}
