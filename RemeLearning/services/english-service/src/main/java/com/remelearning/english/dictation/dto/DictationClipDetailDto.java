package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Full detail for one clip, returned only when the learner opens it to practice sentence-by-sentence
 * (GET /api/v1/dictation/clips/{clipId}) - unlike {@link DictationLessonSummaryDto}, this carries the
 * reference script, since sentence-mode grading happens client-side against these sentences.
 */
@Getter
@Builder
public class DictationClipDetailDto {
	private Long clipId;
	private String code;
	private String title;
	private String audioUrl;
	private String scriptText;
	private List<DictationSentenceDto> sentences;
}
