package com.remelearning.bff.dto;

import lombok.Data;

/** One row of the mistake-comparison table, proxied from english-service. */
@Data
public class DictationErrorEntryDto {
	private String original;
	private String transcribed;
	private String category;
	private String note;
}
