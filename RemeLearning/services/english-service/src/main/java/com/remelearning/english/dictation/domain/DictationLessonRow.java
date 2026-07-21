package com.remelearning.english.dictation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One lesson (clip) inside a folder, joined with the requesting learner's own progress on it -
 * how many sentences it has, how many times they've attempted it, and the accuracy of their most
 * recent attempt - for the folder's lesson-browsing grid. {@code attemptCount}/{@code
 * latestAccuracy} are null when the learner has never attempted this clip.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictationLessonRow {
	private Long clipId;
	private String code;
	private String title;
	private String level;
	private Integer sentenceCount;
	private Integer attemptCount;
	private Double latestAccuracy;
}
