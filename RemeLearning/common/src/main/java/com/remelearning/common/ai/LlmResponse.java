package com.remelearning.common.ai;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LlmResponse {

	private String content;
	private String model;
	private int promptTokens;
	private int completionTokens;
}
