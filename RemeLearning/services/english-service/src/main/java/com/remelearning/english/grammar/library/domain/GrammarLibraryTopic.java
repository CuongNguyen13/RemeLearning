package com.remelearning.english.grammar.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One row in {@code grammar_library_topics} — a fixed, seeded grammar topic (e.g. "Present Simple"). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarLibraryTopic {
	private Long id;
	private String code;
	private String name;
	private String description;
	private String level;
	private int sequenceOrder;
	private Instant createdAt;
}
