package com.remelearning.english.dictation.analyzer;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * The AI feedback for one dictation attempt: short actionable suggestions (Vietnamese, for the
 * learner) and practice sentences targeting the words they missed (later voiced by Supertonic).
 */
@Getter
@Builder
public class DictationAnalysis {
	private List<String> suggestions;
	private List<String> practiceSentences;
}
