package com.remelearning.bff.client;

import com.remelearning.bff.dto.DictationAttemptRequestDto;
import com.remelearning.bff.dto.DictationAttemptResultDto;
import com.remelearning.bff.dto.DictationAttemptDetailDto;
import com.remelearning.bff.dto.DictationClipDetailDto;
import com.remelearning.bff.dto.DictationClipDto;
import com.remelearning.bff.dto.DictationFacetsDto;
import com.remelearning.bff.dto.DictationFolderDto;
import com.remelearning.bff.dto.DictationHistoryEntryDto;
import com.remelearning.bff.dto.DictationLessonSummaryDto;
import com.remelearning.bff.dto.DictationPracticeItemDetailDto;
import com.remelearning.bff.dto.DictationPracticeItemDto;
import com.remelearning.bff.dto.GenerateAiPracticeRequestDto;
import com.remelearning.bff.dto.PracticeRedoRequestDto;
import com.remelearning.bff.dto.StartDictationSessionRequestDto;
import com.remelearning.bff.dto.WeakPointDto;
import com.remelearning.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
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

	public EnglishServiceClient(@Qualifier("englishServiceWebClient") WebClient englishServiceClient) {
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

	/** Proxies a graded redo-exercise submission straight through to english-service's practice endpoint. */
	public Mono<ApiResponse<Void>> redoPractice(PracticeRedoRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/practice/redo")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<Void>>() {})
				.doOnError(ex -> log.error("Failed to proxy practice redo for userId={}", request.getUserId(), ex));
	}

	/** Fetches the dictation-library filter facets from english-service. */
	public Mono<DictationFacetsDto> getDictationFacets() {
		return englishServiceClient.get()
				.uri("/api/v1/dictation/facets")
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<DictationFacetsDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch dictation facets", ex));
	}

	/** Browses library clips filtered by any subset of the taxonomy facets. */
	public Mono<List<DictationClipDto>> listDictationClips(
			String skill, String level, String topic, String examType, int limit) {
		return englishServiceClient.get()
				.uri(uriBuilder -> uriBuilder.path("/api/v1/dictation/clips")
						.queryParamIfPresent("skill", java.util.Optional.ofNullable(skill))
						.queryParamIfPresent("level", java.util.Optional.ofNullable(level))
						.queryParamIfPresent("topic", java.util.Optional.ofNullable(topic))
						.queryParamIfPresent("examType", java.util.Optional.ofNullable(examType))
						.queryParam("limit", limit)
						.build())
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<DictationClipDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to list dictation clips", ex));
	}

	/** Proxies a request for a new dictation session (a batch of library clips) to english-service. */
	public Mono<List<DictationClipDto>> startDictationSession(String userId, StartDictationSessionRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/dictation/sessions/{userId}", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<DictationClipDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to start dictation session for userId={}", userId, ex));
	}

	/** Proxies a graded dictation attempt straight through to english-service. */
	public Mono<DictationAttemptResultDto> submitDictationAttempt(DictationAttemptRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/dictation/attempts")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<DictationAttemptResultDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to submit dictation attempt for userId={}", request.getUserId(), ex));
	}

	/** Fetches a learner's past dictation attempts from english-service. */
	public Mono<List<DictationHistoryEntryDto>> getDictationHistory(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/dictation/history/{userId}", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<DictationHistoryEntryDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch dictation history for userId={}", userId, ex));
	}

	/** Fetches full detail (reference text, transcript, mistakes, AI suggestions) for one of a learner's own past attempts. */
	public Mono<DictationAttemptDetailDto> getDictationAttemptDetail(String userId, Long attemptId) {
		return englishServiceClient.get()
				.uri("/api/v1/dictation/history/{userId}/{attemptId}", userId, attemptId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<DictationAttemptDetailDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch dictation attempt detail for userId={}, attemptId={}", userId, attemptId, ex));
	}

	/** Fetches a learner's AI-practice items (Supertonic-voiced) from english-service. */
	public Mono<List<DictationPracticeItemDto>> getAiPractice(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/dictation/ai-practice/{userId}", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<DictationPracticeItemDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch AI-practice items for userId={}", userId, ex));
	}

	/** Triggers (re)generation of a learner's AI-practice audio in english-service, honoring the requested level/examType facets and translation language. */
	public Mono<List<DictationPracticeItemDto>> generateAiPractice(String userId, GenerateAiPracticeRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/dictation/ai-practice/{userId}/generate", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<DictationPracticeItemDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to generate AI-practice for userId={}", userId, ex));
	}

	/** Triggers AI-practice generation targeted at one specific past attempt's mistakes (the "Luyện tập với AI" history action). */
	public Mono<List<DictationPracticeItemDto>> generateAiPracticeFromAttempt(String userId, Long attemptId, String translationLang) {
		return englishServiceClient.post()
				.uri(uriBuilder -> uriBuilder.path("/api/v1/dictation/history/{userId}/{attemptId}/ai-practice")
						.queryParamIfPresent("translationLang", java.util.Optional.ofNullable(translationLang))
						.build(userId, attemptId))
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<DictationPracticeItemDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to generate AI-practice from attempt for userId={}, attemptId={}", userId, attemptId, ex));
	}

	/** Fetches the dictation-library's folders (topic groupings), each with its lesson count. */
	public Mono<List<DictationFolderDto>> getDictationFolders() {
		return englishServiceClient.get()
				.uri("/api/v1/dictation/folders")
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<DictationFolderDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch dictation folders", ex));
	}

	/** Fetches the light-weight lesson listing for one dictation-library folder, joined with
	 * userId's own progress (sentence count, attempt count, latest accuracy) on each lesson. */
	public Mono<List<DictationLessonSummaryDto>> getDictationFolderLessons(String folderId, String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/dictation/folders/{folderId}/lessons/{userId}", folderId, userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<DictationLessonSummaryDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch dictation lessons for folderId={}, userId={}", folderId, userId, ex));
	}

	/** Fetches full detail (script + sentences, optionally translated) for one dictation clip, for sentence-mode practice. */
	public Mono<DictationClipDetailDto> getDictationClip(Long clipId, String translationLang) {
		return englishServiceClient.get()
				.uri(uriBuilder -> uriBuilder.path("/api/v1/dictation/clips/{clipId}")
						.queryParamIfPresent("translationLang", java.util.Optional.ofNullable(translationLang))
						.build(clipId))
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<DictationClipDetailDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch dictation clip detail for clipId={}", clipId, ex));
	}

	/** Relays a library clip's audio stream (status/headers/body) from english-service to the caller. */
	public Mono<ResponseEntity<Flux<DataBuffer>>> streamClipAudio(Long clipId) {
		return englishServiceClient.get()
				.uri("/api/v1/dictation/clips/{clipId}/audio", clipId)
				.retrieve()
				.toEntityFlux(DataBuffer.class)
				.doOnError(ex -> log.error("Failed to stream dictation clip audio for clipId={}", clipId, ex));
	}

	/** Relays an AI-practice item's synthesized audio stream from english-service to the caller. */
	public Mono<ResponseEntity<Flux<DataBuffer>>> streamPracticeAudio(Long practiceItemId) {
		return englishServiceClient.get()
				.uri("/api/v1/dictation/ai-practice/items/{practiceItemId}/audio", practiceItemId)
				.retrieve()
				.toEntityFlux(DataBuffer.class)
				.doOnError(ex -> log.error("Failed to stream AI-practice audio for practiceItemId={}", practiceItemId, ex));
	}

	/** Fetches full detail (passage text split into sentences) for one AI-practice item, for sentence-mode practice. */
	public Mono<DictationPracticeItemDetailDto> getAiPracticeDetail(Long practiceItemId) {
		return englishServiceClient.get()
				.uri("/api/v1/dictation/ai-practice/items/{practiceItemId}/detail", practiceItemId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<DictationPracticeItemDetailDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch AI-practice item detail for practiceItemId={}", practiceItemId, ex));
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
