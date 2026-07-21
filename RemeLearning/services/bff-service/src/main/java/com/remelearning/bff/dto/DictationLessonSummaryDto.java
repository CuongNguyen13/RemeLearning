package com.remelearning.bff.dto;

import lombok.Data;

/** One lesson (clip) inside a folder, proxied from english-service - no script/sentences yet. */
@Data
public class DictationLessonSummaryDto {
	private Long clipId;
	private String code;
	private String title;
	private String audioUrl;
}
