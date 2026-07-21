package com.remelearning.english.dictation.dto;

import lombok.Builder;
import lombok.Getter;

/** One sentence of a clip's script, with its AI-aligned audio timestamps and translation if available. */
@Getter
@Builder
public class DictationSentenceDto {
	private int index;
	private String text;
	private Integer startMs; // null if the clip hasn't been AI-aligned yet
	private Integer endMs;   // null if the clip hasn't been AI-aligned yet
	private String translation; // null if no translation was requested/generated
}
