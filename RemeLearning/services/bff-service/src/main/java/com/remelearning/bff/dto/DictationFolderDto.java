package com.remelearning.bff.dto;

import lombok.Data;

/** One dictation-library folder (topic grouping), proxied from english-service. */
@Data
public class DictationFolderDto {
	private String folderId;
	private String name;
	private int lessonCount;
}
