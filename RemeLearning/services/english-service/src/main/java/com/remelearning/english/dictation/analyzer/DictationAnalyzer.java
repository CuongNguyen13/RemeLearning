package com.remelearning.english.dictation.analyzer;

import com.remelearning.english.dictation.dto.WordDiffDto;

import java.util.List;

/**
 * Analyzes a learner's dictation attempt into a root-cause-classified mistake breakdown, actionable
 * advice, and targeted practice sentences. Two implementations are selected via
 * {@code dictation.analyzer.mode} ({@code rule-based} default, or {@code llm}), mirroring the
 * vocabulary/grammar classifier pattern. Callers depend on this interface, not the implementation,
 * so the analysis provider can change without touching them.
 */
public interface DictationAnalyzer {

	/**
	 * Never returns {@code null} and never throws - implementations must degrade to a safe template
	 * result rather than propagate a failure, since a flaky call here can't block grading an attempt.
	 *
	 * @param referenceText  the clip's reference transcript
	 * @param userTranscript what the learner actually typed
	 * @param diff           the word-level WER diff between them, in order
	 */
	DictationAnalysis analyzeAttempt(String referenceText, String userTranscript, List<WordDiffDto> diff);

	/**
	 * Practice sentences targeting a learner's recurring missed words, for the "Luyện nghe với AI"
	 * section. Never returns {@code null}/throws; degrades to templates on failure.
	 */
	List<String> generatePracticeSentences(List<String> missedWords);
}
