package com.remelearning.bff.service;

import com.remelearning.bff.client.EnglishServiceClient;
import com.remelearning.bff.dto.WeakPointDto;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WeakPointAggregationServiceTest {

	private final EnglishServiceClient englishServiceClient = mock(EnglishServiceClient.class);
	private final WeakPointAggregationService service = new WeakPointAggregationService(englishServiceClient);

	@Test
	void mergesAllFourCategoriesWhenAllSucceed() {
		WeakPointDto vocab = weakPoint("vocabulary");
		WeakPointDto grammar = weakPoint("grammar");
		WeakPointDto pronunciation = weakPoint("pronunciation");
		WeakPointDto listening = weakPoint("listening");

		when(englishServiceClient.getVocabularyWeakPoints("user-1")).thenReturn(Mono.just(List.of(vocab)));
		when(englishServiceClient.getGrammarWeakPoints("user-1")).thenReturn(Mono.just(List.of(grammar)));
		when(englishServiceClient.getPronunciationWeakPoints("user-1")).thenReturn(Mono.just(List.of(pronunciation)));
		when(englishServiceClient.getListeningWeakPoints("user-1")).thenReturn(Mono.just(List.of(listening)));

		StepVerifier.create(service.getWeakPoints("user-1"))
				.assertNext((Map<String, List<WeakPointDto>> merged) -> {
					assertThat(merged).containsOnlyKeys("vocabulary", "grammar", "pronunciation", "listening");
					assertThat(merged.get("vocabulary")).containsExactly(vocab);
					assertThat(merged.get("grammar")).containsExactly(grammar);
					assertThat(merged.get("pronunciation")).containsExactly(pronunciation);
					assertThat(merged.get("listening")).containsExactly(listening);
				})
				.verifyComplete();
	}

	@Test
	void defaultsFailedCategoryToEmptyListWhileKeepingTheOtherThree() {
		WeakPointDto vocab = weakPoint("vocabulary");
		WeakPointDto pronunciation = weakPoint("pronunciation");
		WeakPointDto listening = weakPoint("listening");

		when(englishServiceClient.getVocabularyWeakPoints("user-1")).thenReturn(Mono.just(List.of(vocab)));
		when(englishServiceClient.getGrammarWeakPoints("user-1")).thenReturn(Mono.error(new RuntimeException("grammar domain down")));
		when(englishServiceClient.getPronunciationWeakPoints("user-1")).thenReturn(Mono.just(List.of(pronunciation)));
		when(englishServiceClient.getListeningWeakPoints("user-1")).thenReturn(Mono.just(List.of(listening)));

		StepVerifier.create(service.getWeakPoints("user-1"))
				.assertNext(merged -> {
					assertThat(merged.get("vocabulary")).containsExactly(vocab);
					assertThat(merged.get("grammar")).isEmpty();
					assertThat(merged.get("pronunciation")).containsExactly(pronunciation);
					assertThat(merged.get("listening")).containsExactly(listening);
				})
				.verifyComplete();
	}

	@Test
	void defaultsFailedListeningCategoryToEmptyListWhileKeepingTheOtherThree() {
		WeakPointDto vocab = weakPoint("vocabulary");
		WeakPointDto grammar = weakPoint("grammar");
		WeakPointDto pronunciation = weakPoint("pronunciation");

		when(englishServiceClient.getVocabularyWeakPoints("user-1")).thenReturn(Mono.just(List.of(vocab)));
		when(englishServiceClient.getGrammarWeakPoints("user-1")).thenReturn(Mono.just(List.of(grammar)));
		when(englishServiceClient.getPronunciationWeakPoints("user-1")).thenReturn(Mono.just(List.of(pronunciation)));
		when(englishServiceClient.getListeningWeakPoints("user-1")).thenReturn(Mono.error(new RuntimeException("listening domain down")));

		StepVerifier.create(service.getWeakPoints("user-1"))
				.assertNext(merged -> {
					assertThat(merged.get("vocabulary")).containsExactly(vocab);
					assertThat(merged.get("grammar")).containsExactly(grammar);
					assertThat(merged.get("pronunciation")).containsExactly(pronunciation);
					assertThat(merged.get("listening")).isEmpty();
				})
				.verifyComplete();
	}

	@Test
	void nextPracticeSetMergesAllCategoriesSortsByForgettingScoreDescAndLimits() {
		WeakPointDto low = weakPoint("vocabulary", 0.2);
		WeakPointDto high = weakPoint("grammar", 0.9);
		WeakPointDto mid = weakPoint("pronunciation", 0.5);
		WeakPointDto listening = weakPoint("listening", 0.1);

		when(englishServiceClient.getVocabularyWeakPoints("user-1")).thenReturn(Mono.just(List.of(low)));
		when(englishServiceClient.getGrammarWeakPoints("user-1")).thenReturn(Mono.just(List.of(high)));
		when(englishServiceClient.getPronunciationWeakPoints("user-1")).thenReturn(Mono.just(List.of(mid)));
		when(englishServiceClient.getListeningWeakPoints("user-1")).thenReturn(Mono.just(List.of(listening)));

		StepVerifier.create(service.getNextPracticeSet("user-1", 2))
				.assertNext(top -> assertThat(top).containsExactly(high, mid))
				.verifyComplete();
	}

	private WeakPointDto weakPoint(String category) {
		return weakPoint(category, 0.5);
	}

	private WeakPointDto weakPoint(String category, double forgettingScore) {
		WeakPointDto dto = new WeakPointDto();
		dto.setItemId("item-1");
		dto.setLabel("label");
		dto.setCategory(category);
		dto.setForgettingScore(forgettingScore);
		dto.setRecommendation("Review " + category);
		return dto;
	}
}
