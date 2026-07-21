package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One lesson (clip) inside a folder, for GET /api/v1/dictation/folders/{folderId}/lessons.
 * Deliberately light - no scriptText/sentences - so the bulk listing stays cheap; the answer is
 * only revealed via {@link DictationClipDetailDto} once the learner opens this specific lesson.
 */
@Getter
@Builder
public class DictationLessonSummaryDto {
	private Long clipId;
	private String code;
	private String title;
	private String audioUrl;
}
