package com.remelearning.english.dictation.scoring;

import com.remelearning.english.dictation.dto.WordDiffDto;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Output of {@link DictationScorer#score(String, String)}. */
@Getter
@Builder
public class DictationScoreResult {
	/** 1 - WER, clamped to [0, 1]. */
	private double accuracy;
	/** Word Error Rate: (substitutions + deletions + insertions) / reference word count. */
	private double wer;
	private List<WordDiffDto> diff;
}
