package com.remelearning.bff.dto;

import lombok.Data;

/** One writing-library topic on one taxonomy axis, with this learner's progress on it. */
@Data
public class WritingLibraryTopicDto {
	private Long topicId;
	/** "grammar" | "genre" | "vocab_theme". */
	private String taxonomy;
	private String code;
	private String name;
	private String description;
	private String level;
	private Integer sequenceOrder;
	/** LOCKED | UNLOCKED | IN_PROGRESS | PASSED. */
	private String status;
	private int passedPromptCount;
	private int targetPromptCount;
}
