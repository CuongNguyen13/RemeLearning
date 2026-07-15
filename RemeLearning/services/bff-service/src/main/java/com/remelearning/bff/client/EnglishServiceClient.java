package com.remelearning.bff.client;

import com.remelearning.bff.dto.WeakPointDto;
import com.remelearning.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Thin wrapper around english-service's REST API (vocabulary/grammar/pronunciation weak points -
 * all three domains live in the one merged english-service on port 8085). Each method stamps its
 * own literal "category" onto the returned DTOs since english-service's per-domain JSON doesn't
 * carry a shared category field (it uses vocabularyType/grammarType/pronunciationType instead).
 */
@Slf4j
@Component
public class EnglishServiceClient {

	private final WebClient englishServiceClient;

	public EnglishServiceClient(@Qualifier("englishServiceClient") WebClient englishServiceClient) {
		this.englishServiceClient = englishServiceClient;
	}

	/** Fetches a learner's vocabulary weak points and tags each one with category="vocabulary". */
	public Mono<List<WeakPointDto>> getVocabularyWeakPoints(String userId) {
		return fetchWeakPoints("/api/v1/vocabulary/weak-points/{userId}", userId, "vocabulary");
	}

	/** Fetches a learner's grammar weak points and tags each one with category="grammar". */
	public Mono<List<WeakPointDto>> getGrammarWeakPoints(String userId) {
		return fetchWeakPoints("/api/v1/grammar/weak-points/{userId}", userId, "grammar");
	}

	/** Fetches a learner's pronunciation weak points and tags each one with category="pronunciation". */
	public Mono<List<WeakPointDto>> getPronunciationWeakPoints(String userId) {
		return fetchWeakPoints("/api/v1/pronunciation/weak-points/{userId}", userId, "pronunciation");
	}

	// Shared GET + unwrap + category-stamp logic for the three (near-identical) domain endpoints above.
	private Mono<List<WeakPointDto>> fetchWeakPoints(String uriTemplate, String userId, String category) {
		return englishServiceClient.get()
				.uri(uriTemplate, userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<WeakPointDto>>>() {})
				.map(ApiResponse::getData)
				.map(weakPoints -> {
					weakPoints.forEach(weakPoint -> weakPoint.setCategory(category));
					return weakPoints;
				})
				.doOnError(ex -> log.error("Failed to fetch {} weak points for userId={}", category, userId, ex));
	}
}
