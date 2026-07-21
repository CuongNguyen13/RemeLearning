package com.remelearning.bff.dto;

import lombok.Data;

/** One lesson (clip) inside a folder, proxied from english-service - no script/sentences yet.
 * level/sentenceCount/attemptCount/latestAccuracy are the requesting learner's own progress on
 * this clip (attemptCount/latestAccuracy null when never attempted). */
@Data
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
