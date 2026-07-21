package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Full detail for one AI-practice item - passage text split into sentences - proxied from english-service. */
@Data
public class DictationPracticeItemDetailDto {
	private Long practiceItemId;
	private String audioUrl;
	private String scriptText;
	private String level;
	private String examType;
	private String topic;
	private List<DictationSentenceDto> sentences;
}
