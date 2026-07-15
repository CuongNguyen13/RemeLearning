package com.remelearning.bff.service;

import com.remelearning.bff.client.EnglishServiceClient;
import com.remelearning.bff.dto.WeakPointDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fans out to english-service's three weak-point endpoints (vocabulary/grammar/pronunciation) in
 * parallel via {@link Mono#zip} and merges them into one map keyed by category. Any one domain
 * call failing degrades that entry to an empty list instead of failing the whole response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeakPointAggregationService {

	private final EnglishServiceClient englishServiceClient;

	/** Fetches all three weak-point categories in parallel and assembles them into a single map. */
	public Mono<Map<String, List<WeakPointDto>>> getWeakPoints(String userId) {
		Mono<List<WeakPointDto>> vocabularyMono = withEmptyFallback(
				englishServiceClient.getVocabularyWeakPoints(userId), userId, "vocabulary");
		Mono<List<WeakPointDto>> grammarMono = withEmptyFallback(
				englishServiceClient.getGrammarWeakPoints(userId), userId, "grammar");
		Mono<List<WeakPointDto>> pronunciationMono = withEmptyFallback(
				englishServiceClient.getPronunciationWeakPoints(userId), userId, "pronunciation");

		return Mono.zip(vocabularyMono, grammarMono, pronunciationMono)
				.map(tuple -> {
					Map<String, List<WeakPointDto>> merged = new LinkedHashMap<>();
					merged.put("vocabulary", tuple.getT1());
					merged.put("grammar", tuple.getT2());
					merged.put("pronunciation", tuple.getT3());
					return merged;
				});
	}

	/**
	 * Builds the next redo-exercise set: merges all three domains' weak points and returns the
	 * top {@code limit} by forgetting score desc - the items most worth re-testing right now.
	 */
	public Mono<List<WeakPointDto>> getNextPracticeSet(String userId, int limit) {
		return getWeakPoints(userId)
				.map(byCategory -> byCategory.values().stream()
						.flatMap(List::stream)
						.sorted(Comparator.comparing(WeakPointDto::getForgettingScore,
								Comparator.nullsLast(Comparator.reverseOrder())))
						.limit(limit)
						.toList());
	}

	// Degrades a failed domain call to an empty list rather than failing the whole aggregated response.
	private Mono<List<WeakPointDto>> withEmptyFallback(Mono<List<WeakPointDto>> mono, String userId, String category) {
		return mono.onErrorResume(ex -> {
			log.warn("english-service ({}) unavailable for userId={}, defaulting to empty list", category, userId, ex);
			return Mono.just(Collections.emptyList());
		});
	}
}
