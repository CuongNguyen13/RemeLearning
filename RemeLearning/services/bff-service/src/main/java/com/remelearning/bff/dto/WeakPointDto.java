package com.remelearning.bff.dto;

import lombok.Data;

/**
 * A single "forgotten"/recurring weak point as surfaced to the UI, merged across english-service's
 * four domains (vocabulary/grammar/pronunciation/listening). Each domain's own weak-point JSON
 * carries a domain-specific type field (vocabularyType/grammarType/pronunciationType/sourceType)
 * instead of a shared "category" - {@link com.remelearning.bff.client.EnglishServiceClient} stamps
 * {@code category} itself with a literal "vocabulary"/"grammar"/"pronunciation"/"listening" after
 * deserializing, based on which endpoint was called, so the merged response can tell the four apart.
 * {@code sourceType} is only populated for category="listening" (DICTATION or COMPREHENSION,
 * mirroring english-service's {@code ListeningSourceType}) and binds automatically via Jackson since
 * english-service's JSON field is named the same.
 */
@Data
public class WeakPointDto {

	private String itemId;
	private String label;
	private String category;
	private Double forgettingScore;
	private String recommendation;
	private String sourceType;
}
