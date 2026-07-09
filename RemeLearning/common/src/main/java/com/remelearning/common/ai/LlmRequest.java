package com.remelearning.common.ai;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class LlmRequest {

	private String systemPrompt;
	private String userPrompt;
	@Builder.Default
	private List<Map<String, String>> history = List.of();
	@Builder.Default
	private double temperature = 0.2;
	private Integer maxOutputTokens;
}
