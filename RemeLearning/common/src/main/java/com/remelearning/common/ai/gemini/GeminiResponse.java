package com.remelearning.common.ai.gemini;

import java.util.List;

/** Wire shape for Gemini's {@code generateContent} response body (only the fields we read). */
record GeminiResponse(List<Candidate> candidates, UsageMetadata usageMetadata) {

	// One generated completion candidate; only the first is used (see GeminiLlmClient).
	record Candidate(Content content) {
	}

	record Content(List<Part> parts) {
	}

	record Part(String text) {
	}

	record UsageMetadata(int promptTokenCount, int candidatesTokenCount) {
	}
}
