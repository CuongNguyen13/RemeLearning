package com.remelearning.english.dictation.scoring;

import com.remelearning.english.dictation.dto.WordDiffDto;
import com.remelearning.english.dictation.dto.WordDiffTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Pure word-level Word-Error-Rate (WER) scorer, comparing a learner's typed transcript against the
 * reference dictation sentence. Case-insensitive and punctuation-normalized; no external dependency
 * (a plain word-level Levenshtein alignment, the same algorithm ASR evaluation tooling uses for WER).
 */
public final class DictationScorer {

	private DictationScorer() {
	}

	public static DictationScoreResult score(String referenceText, String userTranscript) {
		String[] reference = tokenize(referenceText);
		String[] actual = tokenize(userTranscript);

		List<WordDiffDto> diff = align(reference, actual);
		int substitutions = 0;
		int deletions = 0;
		int insertions = 0;
		for (WordDiffDto slot : diff) {
			switch (slot.getTag()) {
				case SUBSTITUTED -> substitutions++;
				case MISSING -> deletions++;
				case EXTRA -> insertions++;
				case CORRECT -> { /* no error */ }
			}
		}

		double wer = reference.length == 0
				? (actual.length == 0 ? 0.0 : 1.0)
				: (double) (substitutions + deletions + insertions) / reference.length;
		double accuracy = Math.max(0.0, Math.min(1.0, 1.0 - wer));

		return DictationScoreResult.builder().accuracy(accuracy).wer(wer).diff(diff).build();
	}

	// Lowercases and strips punctuation (keeping letters, digits and apostrophes, e.g. "don't"),
	// then splits on whitespace; blank input yields an empty token array rather than [""].
	private static String[] tokenize(String text) {
		if (text == null || text.isBlank()) {
			return new String[0];
		}
		String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9' ]", " ").trim();
		return normalized.isEmpty() ? new String[0] : normalized.split("\\s+");
	}

	// Classic Wagner-Fischer edit-distance DP over whole words, then backtraces the optimal path to
	// recover which slots are correct/substituted/missing(deletion)/extra(insertion).
	private static List<WordDiffDto> align(String[] reference, String[] actual) {
		int n = reference.length;
		int m = actual.length;
		int[][] dp = new int[n + 1][m + 1];
		for (int i = 0; i <= n; i++) {
			dp[i][0] = i;
		}
		for (int j = 0; j <= m; j++) {
			dp[0][j] = j;
		}
		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= m; j++) {
				if (reference[i - 1].equals(actual[j - 1])) {
					dp[i][j] = dp[i - 1][j - 1];
				} else {
					dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
				}
			}
		}

		List<WordDiffDto> diff = new ArrayList<>();
		int i = n;
		int j = m;
		while (i > 0 || j > 0) {
			if (i > 0 && j > 0 && reference[i - 1].equals(actual[j - 1]) && dp[i][j] == dp[i - 1][j - 1]) {
				diff.add(WordDiffDto.builder().tag(WordDiffTag.CORRECT)
						.expectedWord(reference[i - 1]).actualWord(actual[j - 1]).build());
				i--;
				j--;
			} else if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
				diff.add(WordDiffDto.builder().tag(WordDiffTag.SUBSTITUTED)
						.expectedWord(reference[i - 1]).actualWord(actual[j - 1]).build());
				i--;
				j--;
			} else if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
				diff.add(WordDiffDto.builder().tag(WordDiffTag.MISSING)
						.expectedWord(reference[i - 1]).actualWord(null).build());
				i--;
			} else {
				diff.add(WordDiffDto.builder().tag(WordDiffTag.EXTRA)
						.expectedWord(null).actualWord(actual[j - 1]).build());
				j--;
			}
		}
		Collections.reverse(diff);
		return diff;
	}
}
