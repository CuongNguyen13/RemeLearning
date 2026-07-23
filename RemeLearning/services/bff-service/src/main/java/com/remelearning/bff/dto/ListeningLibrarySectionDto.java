package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** A listening-library section ready to play/answer: passage text, a fetchable audio URL, and its question pool. */
@Data
public class ListeningLibrarySectionDto {
	private Long sectionId;
	private String passageText;
	private String audioUrl;
	private List<ListeningLibraryQuestionDto> questions;
}
