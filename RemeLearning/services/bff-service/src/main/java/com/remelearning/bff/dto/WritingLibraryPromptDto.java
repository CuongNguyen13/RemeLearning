package com.remelearning.bff.dto;

import lombok.Data;

/** One writing-library prompt as the client sees it; carries no reference answer before submission. */
@Data
public class WritingLibraryPromptDto {
	private Long promptId;
	private Long topicId;
	private String topicName;
	private String taskType;
	private String promptText;
	private String sourceLang;
	private String targetLang;
	private Integer minWords;
	private int position;
	private int targetPromptCount;
}
