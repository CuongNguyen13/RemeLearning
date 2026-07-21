package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** The distinct filter values available across the library, for GET /api/v1/dictation/facets. */
@Getter
@Builder
public class DictationFacetsDto {
	private List<String> skills;
	private List<String> levels;
	private List<String> topics;
	private List<String> examTypes;
	private int minListensForHint;
}
