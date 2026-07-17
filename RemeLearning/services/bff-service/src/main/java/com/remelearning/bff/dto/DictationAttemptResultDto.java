package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** Grading result for a submitted dictation attempt, proxied from english-service. */
@Data
public class DictationAttemptResultDto {
	private String referenceText;
	private double accuracy;
	private double wer;
	private List<WordDiffDto> diff;
	private List<String> aiSuggestions;
	private List<String> practiceSentences;
}
