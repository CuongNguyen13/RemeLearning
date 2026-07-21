package com.remelearning.bff.dto;

import lombok.Data;

/** One sentence of a clip's script, with its AI-aligned audio timestamps and translation if available. */
@Data
public class DictationSentenceDto {
	private int index;
	private String text;
	private Integer startMs;
	private Integer endMs;
	private String translation;
}
