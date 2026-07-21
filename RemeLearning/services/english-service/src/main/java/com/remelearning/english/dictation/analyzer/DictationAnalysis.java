package com.remelearning.english.dictation.analyzer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * The AI feedback for one dictation attempt: a mistake-comparison table classified by root cause
 * (Lexicon/Grammar/Phonology), a root-cause summary per category that actually has misses,
 * actionable advice (Vietnamese, for the learner), and practice sentences targeting the words they
 * missed (later voiced by Supertonic). Setters + no-arg constructor exist alongside the builder so
 * it round-trips through Jackson when persisted as JSON (see DictationServiceImpl).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictationAnalysis {
	private List<DictationErrorEntry> errorTable;
	private List<DictationRootCauseGroup> rootCauses;
	private List<String> actionAdvice;
	private List<String> practiceSentences;
}
