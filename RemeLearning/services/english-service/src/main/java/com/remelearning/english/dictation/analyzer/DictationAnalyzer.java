package com.remelearning.english.dictation.analyzer;

import java.util.List;

/**
 * Analyzes a learner's dictation misses into study suggestions and targeted practice sentences.
 * Two implementations are selected via {@code dictation.analyzer.mode} ({@code rule-based} default,
 * or {@code llm}), mirroring the vocabulary/grammar classifier pattern. Callers depend on this
 * interface, not the implementation, so the analysis provider can change without touching them.
 */
public interface DictationAnalyzer {

	/**
	 * Never returns {@code null} and never throws - implementations must degrade to a safe template
	 * result rather than propagate a failure, since a flaky call here can't block grading an attempt.
	 *
	 * @param referenceText the clip's reference transcript
	 * @param missedWords   the distinct words the learner got wrong (lower-cased), possibly empty
	 */
	DictationAnalysis analyzeAttempt(String referenceText, List<String> missedWords);

	/**
	 * Practice sentences targeting a learner's recurring missed words, for the "Luyện nghe với AI"
	 * section. Never returns {@code null}/throws; degrades to templates on failure.
	 */
	List<String> generatePracticeSentences(List<String> missedWords);
}
