package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Grading result for one submitted {@link DictationAttemptRequest}, revealing the reference text.
 * Also carries the immediate AI feedback: {@code aiSuggestions} (what to focus on, in Vietnamese)
 * and {@code practiceSentences} (sentences persisted for the "Luyện nghe với AI" section).
 */
@Getter
@Builder
public class DictationAttemptResultDto {
	private String referenceText;
	/** 1 - WER, clamped to [0, 1]. */
	private double accuracy;
	private double wer;
	private List<WordDiffDto> diff;
	private List<String> aiSuggestions;
	private List<String> practiceSentences;
}
