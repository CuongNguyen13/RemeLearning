package com.remelearning.english.grammar.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One example sentence (English + Vietnamese meaning) inside {@link GrammarLibraryContent#getExamplesJson()}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarLibraryExample {
	private String en;
	private String vi;
}
