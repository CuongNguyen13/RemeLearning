package com.remelearning.english.dictation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One aggregated row from the misses ledger: a word and how many times the learner has missed it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissWordCount {
	private String word;
	private int missCount;
}
