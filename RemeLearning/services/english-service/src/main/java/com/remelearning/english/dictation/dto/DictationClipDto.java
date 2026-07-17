package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One library clip handed to the learner to listen to and transcribe. Deliberately omits the
 * reference script so the answer isn't leaked before grading; {@code audioUrl} points at the
 * service's own streaming endpoint ({@code GET /api/v1/dictation/clips/{id}/audio}).
 */
@Getter
@Builder
public class DictationClipDto {
	private Long clipId;
	private String code;
	private String title;
	private String skill;
	private String level;
	private String topic;
	private String examType;
	private String audioUrl;
}
