package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Full detail for one clip - script + split sentences - proxied from english-service. */
@Data
public class DictationClipDetailDto {
	private Long clipId;
	private String code;
	private String title;
	private String audioUrl;
	private String scriptText;
	private List<DictationSentenceDto> sentences;
}
