package com.remelearning.bff.dto;

import lombok.Data;

/**
 * A single "forgotten"/recurring weak point as surfaced to the UI, merged across english-service's
 * three domains (vocabulary/grammar/pronunciation). Each domain's own weak-point JSON carries a
 * domain-specific type field (vocabularyType/grammarType/pronunciationType) instead of a shared
 * "category" - {@link com.remelearning.bff.client.EnglishServiceClient} stamps {@code category}
 * itself with a literal "vocabulary"/"grammar"/"pronunciation" after deserializing, based on which
 * endpoint was called, so the merged response can tell the three apart.
 */
@Data
public class WeakPointDto {

	private String itemId;
	private String label;
	private String category;
	private Double forgettingScore;
	private String recommendation;
}
