package com.remelearning.english.vocabulary.library.dto;

import lombok.Data;

/** Optional facets when starting a new Section; {@code sectionSize} is clamped server-side to [5,20], default 10. */
@Data
public class StartSectionRequest {
	private Integer sectionSize;
}
