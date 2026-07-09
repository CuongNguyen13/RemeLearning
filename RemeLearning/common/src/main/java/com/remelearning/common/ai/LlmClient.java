package com.remelearning.common.ai;

/**
 * Vendor-neutral contract for calling a large language model.
 * Concrete implementations (e.g. Claude, GPT-4) live outside {@code common}
 * so the LLM provider can be swapped per-service without touching callers.
 */
public interface LlmClient {

	/** Sends a prompt to the underlying LLM and returns its completion. */
	LlmResponse complete(LlmRequest request);
}
