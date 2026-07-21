package com.remelearning.english.dictation.dto;

import com.remelearning.english.dictation.analyzer.DictationErrorEntry;
import com.remelearning.english.dictation.analyzer.DictationRootCauseGroup;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Grading result for one submitted {@link DictationAttemptRequest}, revealing the reference text.
 * Also carries the immediate AI feedback: {@code errorTable}/{@code rootCauses} (root-cause
 * classified mistakes, in Vietnamese), {@code actionAdvice} (what to focus on), and
 * {@code practiceSentences} (sentences persisted for the "Luyện nghe với AI" section).
 */
@Getter
@Builder
public class DictationAttemptResultDto {
	private String referenceText;
	/** 1 - WER, clamped to [0, 1]. */
	private double accuracy;
	private double wer;
	private List<WordDiffDto> diff;
	private List<DictationErrorEntry> errorTable;
	private List<DictationRootCauseGroup> rootCauses;
	private List<String> actionAdvice;
	private List<String> practiceSentences;
}
