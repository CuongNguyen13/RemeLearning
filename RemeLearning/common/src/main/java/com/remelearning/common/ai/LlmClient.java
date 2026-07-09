package com.remelearning.common.ai;

public interface LlmClient {

	LlmResponse complete(LlmRequest request);
}
