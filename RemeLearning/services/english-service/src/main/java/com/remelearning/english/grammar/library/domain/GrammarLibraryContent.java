package com.remelearning.english.grammar.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row in {@code grammar_library_contents} — the AI-generated theory page for a topic,
 * generated once on first read and reused forever after (never regenerated).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarLibraryContent {
	private Long id;
	private Long topicId;
	private String explanationEn;
	private String explanationVi;
	/** A mermaid diagram or a markdown-table sentence structure illustrating the rule. */
	private String illustrationText;
	/** JSON array of {@link GrammarLibraryExample}. */
	private String examplesJson;
	private Instant generatedAt;
}
