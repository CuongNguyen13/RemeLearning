package com.remelearning.common.ai.align.aiservice;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire response element from ai-service's {@code POST /api/v1/dictation/align-sentences}
 * (snake_case keys). Package-private - never exposed outside this implementation package.
 */
record AiServiceSentenceTimingResponse(
		@JsonProperty("start_ms") Integer startMs,
		@JsonProperty("end_ms") Integer endMs) {
}
