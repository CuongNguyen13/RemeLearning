package com.remelearning.english.writing.library.dto;

import lombok.Builder;
import lombok.Data;

/** One catalogue topic plus this learner's gating status on it. */
@Data
@Builder
public class WritingLibraryTopicDto {
	private Long topicId;
	private String taxonomy;
	private String code;
	private String name;
	private String description;
	private String level;
	private Integer sequenceOrder;
	/** {@code LOCKED} | {@code UNLOCKED} | {@code IN_PROGRESS} | {@code PASSED}. */
	private String status;
	/** How many prompts in this topic's chain the learner has already passed. */
	private int passedPromptCount;
	/** How many prompts the chain will hold in total once fully generated. */
	private int targetPromptCount;
}
