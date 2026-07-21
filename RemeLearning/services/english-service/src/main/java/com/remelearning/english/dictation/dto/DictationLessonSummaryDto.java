package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One lesson (clip) inside a folder, for GET /api/v1/dictation/folders/{folderId}/lessons/{userId}.
 * Deliberately light - no scriptText/sentences - so the bulk listing stays cheap; the answer is
 * only revealed via {@link DictationClipDetailDto} once the learner opens this specific lesson.
 * {@code sentenceCount} stands in for a duration estimate (no audio-length data is stored);
 * {@code attemptCount}/{@code latestAccuracy} are null when the learner has never attempted this
 * clip, and drive the FE's progress badge ("Chưa bắt đầu" / "X% hoàn thành" / "Hoàn thành").
 */
@Getter
@Builder
public class DictationLessonSummaryDto {
	private Long clipId;
	private String code;
	private String title;
	private String audioUrl;
	private String level;
	private Integer sentenceCount;
	private Integer attemptCount;
	private Double latestAccuracy;
}
