package com.remelearning.bff.dto;

import lombok.Data;

/** One labelled mistake the AI grader found; its label/category feed the weak-point pipeline. */
@Data
public class WritingErrorDto {
	private String wrong;
	private String corrected;
	private String label;
	/** "grammar" or "vocabulary". */
	private String category;
	private String explanationVi;
	private String severity;
}
