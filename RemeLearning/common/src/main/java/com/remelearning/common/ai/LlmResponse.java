package com.remelearning.common.ai;

import lombok.Builder;
import lombok.Getter;

/** Result of an {@link LlmClient} completion call. */
@Getter
@Builder
public class LlmResponse {

	/** The model's raw text completion. */
	private String content;
	/** Identifier of the model that produced this response, for auditing/cost tracking. */
	private String model;
	private int promptTokens;
	private int completionTokens;
}
