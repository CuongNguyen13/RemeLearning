package com.remelearning.english.dictation.analyzer;

import com.remelearning.english.dictation.dto.WordDiffDto;
import com.remelearning.english.dictation.dto.WordDiffTag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Default {@link DictationAnalyzer}: static templates, no LLM cost. Active unless
 * {@code dictation.analyzer.mode=llm} (see {@link LlmDictationAnalyzer}).
 */
@Component
@ConditionalOnProperty(prefix = "dictation.analyzer", name = "mode", havingValue = "rule-based", matchIfMissing = true)
public class RuleBasedDictationAnalyzer implements DictationAnalyzer {

	// Classifies each wrong slot via DictationAnalysisTemplates.classify, groups them into
	// per-category root-cause summaries (only categories that actually occurred), and reuses the
	// existing static advice/practice-sentence templates.
	@Override
	public DictationAnalysis analyzeAttempt(String referenceText, String userTranscript, List<WordDiffDto> diff) {
		List<DictationErrorEntry> errorTable = new ArrayList<>();
		Map<DictationErrorCategory, List<String>> byCategory = new LinkedHashMap<>();
		for (WordDiffDto slot : diff) {
			if (slot.getTag() != WordDiffTag.MISSING && slot.getTag() != WordDiffTag.SUBSTITUTED) {
				continue;
			}
			DictationErrorCategory category = DictationAnalysisTemplates.classify(slot.getExpectedWord(), slot.getActualWord());
			errorTable.add(DictationErrorEntry.builder()
					.original(slot.getExpectedWord())
					.transcribed(slot.getActualWord())
					.category(category)
					.build());
			byCategory.computeIfAbsent(category, c -> new ArrayList<>()).add(slot.getExpectedWord());
		}

		List<DictationRootCauseGroup> rootCauses = byCategory.entrySet().stream()
				.map(entry -> DictationRootCauseGroup.builder()
						.category(entry.getKey())
						.summary(DictationAnalysisTemplates.summaryFor(entry.getKey()))
						.examples(entry.getValue())
						.build())
				.toList();
		List<String> actionAdvice = byCategory.isEmpty()
				? DictationAnalysisTemplates.praiseAdvice()
				: byCategory.keySet().stream().map(DictationAnalysisTemplates::adviceFor).toList();

		List<String> missedWords = distinctExpectedWords(diff);
		return DictationAnalysis.builder()
				.errorTable(errorTable)
				.rootCauses(rootCauses)
				.actionAdvice(actionAdvice)
				.practiceSentences(DictationAnalysisTemplates.practiceSentencesFor(missedWords))
				.build();
	}

	@Override
	public List<String> generatePracticeSentences(List<String> missedWords) {
		return DictationAnalysisTemplates.practiceSentencesFor(missedWords);
	}

	private List<String> distinctExpectedWords(List<WordDiffDto> diff) {
		LinkedHashSet<String> words = new LinkedHashSet<>();
		for (WordDiffDto slot : diff) {
			if (slot.getTag() == WordDiffTag.MISSING || slot.getTag() == WordDiffTag.SUBSTITUTED) {
				words.add(slot.getExpectedWord().toLowerCase());
			}
		}
		return new ArrayList<>(words);
	}
}
