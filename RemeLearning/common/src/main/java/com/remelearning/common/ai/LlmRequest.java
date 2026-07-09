package com.remelearning.common.ai;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/** Input payload for an {@link LlmClient} completion call. */
@Getter
@Builder
public class LlmRequest {

	/** Instruction that sets the model's behavior/persona. */
	private String systemPrompt;
	/** The actual question/content to analyze. */
	private String userPrompt;
	/** Prior turns, oldest first, each entry shaped like {"role": ..., "content": ...}. */
	@Builder.Default
	private List<Map<String, String>> history = List.of();
	/** Sampling temperature; kept low by default for deterministic grading/analysis tasks. */
	@Builder.Default
	private double temperature = 0.2;
	/** Optional cap on the completion length. */
	private Integer maxOutputTokens;
}
