package com.remelearning.common.ai.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Wire shape for Gemini's {@code generateContent} request body. */
@JsonInclude(JsonInclude.Include.NON_NULL)
record GeminiRequest(
		SystemInstruction systemInstruction,
		List<Content> contents,
		GenerationConfig generationConfig) {

	// The model's persona/instructions, sent separately from the conversation turns.
	record SystemInstruction(List<Part> parts) {
	}

	// One conversation turn; role is "user" or "model".
	record Content(String role, List<Part> parts) {
	}

	record Part(String text) {
	}

	record GenerationConfig(double temperature, Integer maxOutputTokens) {
	}
}
