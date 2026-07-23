package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** A speaking-library section ready to practice: its pool of sample sentences, each with IPA + a fetchable sample-audio URL. */
@Data
public class SpeakingLibrarySectionDto {
	private Long sectionId;
	private List<SpeakingLibrarySentenceDto> sentences;
}
