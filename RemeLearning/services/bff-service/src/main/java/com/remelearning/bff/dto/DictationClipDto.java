package com.remelearning.bff.dto;

import lombok.Data;

/**
 * One library clip for the learner to listen to and transcribe, proxied from english-service.
 * Has no script text - only taxonomy + a relative audio URL (the FE builds a bff-relative audio URL
 * from {@code clipId}).
 */
@Data
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
