package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

@Data
public class SectionCardDto {
	private Long sectionId;
	private String cardKind;
	private Long libraryWordId;
	private String word;
	private String meaningVi;
	private String exampleEn;
	private String audioUrl;
	private String exerciseType;
	private String prompt;
	private List<String> options;
	private SectionProgressDto progress;
}
