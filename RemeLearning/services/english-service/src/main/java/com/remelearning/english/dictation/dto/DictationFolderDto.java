package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

/** One folder (topic grouping) in the dictation library, for GET /api/v1/dictation/folders. */
@Getter
@Builder
public class DictationFolderDto {
	private String folderId;
	private String name;
	private int lessonCount;
}
