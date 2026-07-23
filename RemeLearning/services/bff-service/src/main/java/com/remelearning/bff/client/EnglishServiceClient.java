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
import com.remelearning.bff.dto.FinishGrammarLibrarySessionResponseDto;
import com.remelearning.bff.dto.FinishSpeakingSectionResponse;
import com.remelearning.bff.dto.GenerateAiPracticeRequestDto;
import com.remelearning.bff.dto.PracticeRedoRequestDto;
import com.remelearning.bff.dto.GenerateGrammarPracticeRequestDto;
import com.remelearning.bff.dto.GenerateVocabPracticeRequestDto;
import com.remelearning.bff.dto.GrammarAttemptDetailDto;
import com.remelearning.bff.dto.GrammarAttemptHistoryEntryDto;
import com.remelearning.bff.dto.GrammarAttemptResultDto;
import com.remelearning.bff.dto.GrammarLibraryAnswerResultDto;
import com.remelearning.bff.dto.GrammarLibraryContentDto;
import com.remelearning.bff.dto.GrammarLibraryHistoryEntryDto;
import com.remelearning.bff.dto.GrammarLibraryTopicDto;
import com.remelearning.bff.dto.GrammarPracticeItemDto;
import com.remelearning.bff.dto.GenerateListeningPracticeRequestDto;
import com.remelearning.bff.dto.ListeningAttemptDetailDto;
import com.remelearning.bff.dto.ListeningAttemptHistoryEntryDto;
import com.remelearning.bff.dto.ListeningAttemptResultDto;
import com.remelearning.bff.dto.ListeningLibraryHistoryEntryDto;
import com.remelearning.bff.dto.ListeningLibrarySectionDto;
import com.remelearning.bff.dto.ListeningLibraryTopicDto;
import com.remelearning.bff.dto.ListeningPracticeItemDto;
import com.remelearning.bff.dto.GenerateSpeakingPracticeRequestDto;
import com.remelearning.bff.dto.SentenceAttemptResultDto;
import com.remelearning.bff.dto.SpeakingAttemptDetailDto;
import com.remelearning.bff.dto.SpeakingAttemptHistoryEntryDto;
import com.remelearning.bff.dto.SpeakingAttemptResultDto;
import com.remelearning.bff.dto.SpeakingLibraryHistoryEntryDto;
import com.remelearning.bff.dto.SpeakingLibrarySectionDto;
import com.remelearning.bff.dto.SpeakingLibraryTopicDto;
import com.remelearning.bff.dto.SpeakingPracticeItemDto;
import com.remelearning.bff.dto.StartDictationSessionRequestDto;
import com.remelearning.bff.dto.SectionAnswerResultDto;
import com.remelearning.bff.dto.SectionCardDto;
import com.remelearning.bff.dto.SectionHistoryEntryDto;
import com.remelearning.bff.dto.StartGrammarSessionResponseDto;
import com.remelearning.bff.dto.StartSectionRequestDto;
import com.remelearning.bff.dto.SubmitGrammarAttemptRequestDto;
import com.remelearning.bff.dto.SubmitGrammarLibraryAnswerRequestDto;
import com.remelearning.bff.dto.SubmitListeningAnswersRequest;
import com.remelearning.bff.dto.SubmitListeningAnswersResponse;
import com.remelearning.bff.dto.SubmitListeningAttemptRequestDto;
import com.remelearning.bff.dto.SubmitSectionAnswerRequestDto;
import com.remelearning.bff.dto.SubmitVocabAttemptRequestDto;
import com.remelearning.bff.dto.TopicSummaryDto;
import com.remelearning.bff.dto.VocabAttemptDetailDto;
import com.remelearning.bff.dto.VocabAttemptHistoryEntryDto;
import com.remelearning.bff.dto.VocabAttemptResultDto;
import com.remelearning.bff.dto.VocabPracticeItemDto;
import com.remelearning.bff.dto.WeakPointDto;
import com.remelearning.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
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

	/** Generates one AI vocabulary practice set, targeting the given focus words or (if omitted) the learner's own top weak points. */
	public Mono<VocabPracticeItemDto> generateVocabPractice(String userId, GenerateVocabPracticeRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/vocabulary/{userId}/generate", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<VocabPracticeItemDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to generate vocabulary practice for userId={}", userId, ex));
	}

	/** Fetches full detail (questions, no answers) for one vocabulary practice set. */
	public Mono<VocabPracticeItemDto> getVocabPracticeItem(Long itemId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/vocabulary/items/{itemId}", itemId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<VocabPracticeItemDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch vocabulary practice item for itemId={}", itemId, ex));
	}

	/** Fetches a learner's generated vocabulary practice sets, newest first. */
	public Mono<List<VocabPracticeItemDto>> listVocabPracticeItems(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/vocabulary/{userId}/items", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<VocabPracticeItemDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to list vocabulary practice items for userId={}", userId, ex));
	}

	/** Proxies a graded vocabulary-practice attempt straight through to english-service. */
	public Mono<VocabAttemptResultDto> submitVocabAttempt(SubmitVocabAttemptRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/vocabulary/attempts")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<VocabAttemptResultDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to submit vocabulary practice attempt for userId={}", request.getUserId(), ex));
	}

	/** Fetches a learner's past vocabulary-practice attempts from english-service. */
	public Mono<List<VocabAttemptHistoryEntryDto>> getVocabPracticeHistory(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/vocabulary/history/{userId}", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<VocabAttemptHistoryEntryDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch vocabulary practice history for userId={}", userId, ex));
	}

	/** Fetches full detail for one of a learner's own past vocabulary-practice attempts. */
	public Mono<VocabAttemptDetailDto> getVocabAttemptDetail(String userId, Long attemptId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/vocabulary/history/{userId}/{attemptId}", userId, attemptId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<VocabAttemptDetailDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch vocabulary attempt detail for userId={}, attemptId={}", userId, attemptId, ex));
	}

	/** Fetches every topic with its word count and this learner's mastered-word count. */
	public Mono<List<TopicSummaryDto>> listVocabLibraryTopics(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/vocabulary/library/{userId}/topics", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<TopicSummaryDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to list vocabulary library topics for userId={}", userId, ex));
	}

	/** Starts a new Section for a topic; returns the first card. */
	public Mono<SectionCardDto> startVocabSection(String userId, Long topicId, StartSectionRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/vocabulary/library/{userId}/topics/{topicId}/sections", userId, topicId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<SectionCardDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to start vocabulary section for userId={}, topicId={}", userId, topicId, ex));
	}

	/** Grades the current card's answer (or acknowledges an INTRO) and returns the next card, or a completed result. */
	public Mono<SectionAnswerResultDto> submitVocabSectionAnswer(Long sectionId, SubmitSectionAnswerRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/vocabulary/library/sections/{sectionId}/answers", sectionId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<SectionAnswerResultDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to submit vocabulary section answer for sectionId={}", sectionId, ex));
	}

	/** Ends an in-progress Section early. */
	public Mono<SectionAnswerResultDto> finishVocabSection(Long sectionId) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/vocabulary/library/sections/{sectionId}/finish", sectionId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<SectionAnswerResultDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to finish vocabulary section for sectionId={}", sectionId, ex));
	}

	/** Fetches a learner's finished Sections, newest first. */
	public Mono<List<SectionHistoryEntryDto>> getVocabSectionHistory(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/vocabulary/library/{userId}/sections/history", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<SectionHistoryEntryDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to get vocabulary section history for userId={}", userId, ex));
	}

	/** Relays one library word's synthesized pronunciation audio stream from english-service to the caller. */
	public Mono<ResponseEntity<Flux<DataBuffer>>> streamVocabLibraryWordAudio(Long wordId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/vocabulary/library/words/{wordId}/audio", wordId)
				.retrieve()
				.toEntityFlux(DataBuffer.class)
				.doOnError(ex -> log.error("Failed to stream vocabulary library word audio for wordId={}", wordId, ex));
	}

	/** Fetches every one of the 60 grammar-library catalog topics with this learner's own progression status. */
	public Mono<List<GrammarLibraryTopicDto>> listGrammarLibraryTopics(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/grammar/library/{userId}/topics", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<GrammarLibraryTopicDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to list grammar library topics for userId={}", userId, ex));
	}

	/** Fetches a grammar-library topic's theory page + question pool, generated via AI on first read only. */
	public Mono<GrammarLibraryContentDto> getGrammarLibraryTopicContent(Long topicId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/grammar/library/topics/{topicId}", topicId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<GrammarLibraryContentDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to get grammar library topic content for topicId={}", topicId, ex));
	}

	/** Starts a new INITIAL grammar-library session for a topic (topic must be UNLOCKED or IN_PROGRESS). */
	public Mono<StartGrammarSessionResponseDto> startGrammarLibrarySession(String userId, Long topicId) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/grammar/library/{userId}/topics/{topicId}/sessions", userId, topicId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<StartGrammarSessionResponseDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to start grammar library session for userId={}, topicId={}", userId, topicId, ex));
	}

	/** Grades one submitted answer within an in-progress grammar-library session. */
	public Mono<GrammarLibraryAnswerResultDto> submitGrammarLibraryAnswer(Long sessionId, SubmitGrammarLibraryAnswerRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/grammar/library/sessions/{sessionId}/answers", sessionId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<GrammarLibraryAnswerResultDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to submit grammar library answer for sessionId={}", sessionId, ex));
	}

	/** Finishes a grammar-library session: PASSED + next-topic unlock when all correct, otherwise a fresh RETRY session. */
	public Mono<FinishGrammarLibrarySessionResponseDto> finishGrammarLibrarySession(Long sessionId) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/grammar/library/sessions/{sessionId}/finish", sessionId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<FinishGrammarLibrarySessionResponseDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to finish grammar library session for sessionId={}", sessionId, ex));
	}

	/** Fetches a learner's completed grammar-library sessions for one topic, newest first. */
	public Mono<List<GrammarLibraryHistoryEntryDto>> getGrammarLibraryHistory(String userId, Long topicId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/grammar/library/{userId}/topics/{topicId}/history", userId, topicId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<GrammarLibraryHistoryEntryDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to get grammar library history for userId={}, topicId={}", userId, topicId, ex));
	}

	/** Generates one AI grammar practice set, targeting the given focus rules or (if omitted) the learner's own top weak points. */
	public Mono<GrammarPracticeItemDto> generateGrammarPractice(String userId, GenerateGrammarPracticeRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/grammar/{userId}/generate", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<GrammarPracticeItemDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to generate grammar practice for userId={}", userId, ex));
	}

	/** Fetches full detail (questions, no answers) for one grammar practice set. */
	public Mono<GrammarPracticeItemDto> getGrammarPracticeItem(Long itemId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/grammar/items/{itemId}", itemId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<GrammarPracticeItemDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch grammar practice item for itemId={}", itemId, ex));
	}

	/** Fetches a learner's generated grammar practice sets, newest first. */
	public Mono<List<GrammarPracticeItemDto>> listGrammarPracticeItems(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/grammar/{userId}/items", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<GrammarPracticeItemDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to list grammar practice items for userId={}", userId, ex));
	}

	/** Proxies a graded grammar-practice attempt straight through to english-service. */
	public Mono<GrammarAttemptResultDto> submitGrammarAttempt(SubmitGrammarAttemptRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/grammar/attempts")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<GrammarAttemptResultDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to submit grammar practice attempt for userId={}", request.getUserId(), ex));
	}

	/** Fetches a learner's past grammar-practice attempts from english-service. */
	public Mono<List<GrammarAttemptHistoryEntryDto>> getGrammarPracticeHistory(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/grammar/history/{userId}", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<GrammarAttemptHistoryEntryDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch grammar practice history for userId={}", userId, ex));
	}

	/** Fetches full detail for one of a learner's own past grammar-practice attempts. */
	public Mono<GrammarAttemptDetailDto> getGrammarAttemptDetail(String userId, Long attemptId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/grammar/history/{userId}/{attemptId}", userId, attemptId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<GrammarAttemptDetailDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch grammar attempt detail for userId={}, attemptId={}", userId, attemptId, ex));
	}

	/** Generates one AI listening passage (Gemini transcript+questions, Supertonic audio), targeting the given focus keywords or (if omitted) the learner's own recently-missed keywords. */
	public Mono<ListeningPracticeItemDto> generateListeningPractice(String userId, GenerateListeningPracticeRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/listening/{userId}/generate", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<ListeningPracticeItemDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to generate listening practice for userId={}", userId, ex));
	}

	/** Fetches full detail (questions, no transcript/answers) for one listening practice item. */
	public Mono<ListeningPracticeItemDto> getListeningPracticeItem(Long itemId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/listening/items/{itemId}", itemId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<ListeningPracticeItemDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch listening practice item for itemId={}", itemId, ex));
	}

	/** Fetches a learner's generated listening practice items, newest first. */
	public Mono<List<ListeningPracticeItemDto>> listListeningPracticeItems(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/listening/{userId}/items", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<ListeningPracticeItemDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to list listening practice items for userId={}", userId, ex));
	}

	/** Relays one listening practice item's synthesized audio stream from english-service to the caller. */
	public Mono<ResponseEntity<Flux<DataBuffer>>> streamListeningAudio(Long itemId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/listening/items/{itemId}/audio", itemId)
				.retrieve()
				.toEntityFlux(DataBuffer.class)
				.doOnError(ex -> log.error("Failed to stream listening practice audio for itemId={}", itemId, ex));
	}

	/** Proxies a graded listening-practice attempt straight through to english-service. */
	public Mono<ListeningAttemptResultDto> submitListeningAttempt(SubmitListeningAttemptRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/listening/attempts")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<ListeningAttemptResultDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to submit listening practice attempt for userId={}", request.getUserId(), ex));
	}

	/** Fetches a learner's past listening-practice attempts from english-service. */
	public Mono<List<ListeningAttemptHistoryEntryDto>> getListeningPracticeHistory(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/listening/history/{userId}", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<ListeningAttemptHistoryEntryDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch listening practice history for userId={}", userId, ex));
	}

	/** Fetches full detail for one of a learner's own past listening-practice attempts. */
	public Mono<ListeningAttemptDetailDto> getListeningAttemptDetail(String userId, Long attemptId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/listening/history/{userId}/{attemptId}", userId, attemptId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<ListeningAttemptDetailDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch listening attempt detail for userId={}, attemptId={}", userId, attemptId, ex));
	}

	/** Generates one AI speaking-practice sentence with a Supertonic sample recording, targeting the given focus words or (if omitted) the learner's own top pronunciation weak points. */
	public Mono<SpeakingPracticeItemDto> generateSpeakingPractice(String userId, GenerateSpeakingPracticeRequestDto request) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/speaking/{userId}/generate", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<SpeakingPracticeItemDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to generate speaking practice for userId={}", userId, ex));
	}

	/** Fetches full detail (target text + sample audio URL) for one speaking practice item. */
	public Mono<SpeakingPracticeItemDto> getSpeakingPracticeItem(Long itemId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/speaking/items/{itemId}", itemId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<SpeakingPracticeItemDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch speaking practice item for itemId={}", itemId, ex));
	}

	/** Fetches a learner's generated speaking practice items, newest first. */
	public Mono<List<SpeakingPracticeItemDto>> listSpeakingPracticeItems(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/speaking/{userId}/items", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<SpeakingPracticeItemDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to list speaking practice items for userId={}", userId, ex));
	}

	/** Relays one speaking practice item's Supertonic sample audio stream from english-service to the caller. */
	public Mono<ResponseEntity<Flux<DataBuffer>>> streamSpeakingSampleAudio(Long itemId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/speaking/items/{itemId}/sample-audio", itemId)
				.retrieve()
				.toEntityFlux(DataBuffer.class)
				.doOnError(ex -> log.error("Failed to stream speaking sample audio for itemId={}", itemId, ex));
	}

	/** Streams a learner's recorded attempt straight through to english-service's multipart submit endpoint, without buffering it in bff-service. */
	public Mono<SpeakingAttemptResultDto> submitSpeakingAttempt(String userId, Long practiceItemId, FilePart audio) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.asyncPart("audio", audio.content(), DataBuffer.class)
				.headers(headers -> headers.setContentDispositionFormData("audio", audio.filename()));
		builder.part("practiceItemId", practiceItemId.toString());

		return englishServiceClient.post()
				.uri("/api/v1/learn/speaking/{userId}/attempts", userId)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(BodyInserters.fromMultipartData(builder.build()))
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<SpeakingAttemptResultDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to submit speaking attempt for userId={}, practiceItemId={}", userId, practiceItemId, ex));
	}

	/** Fetches a learner's past speaking-practice attempts from english-service. */
	public Mono<List<SpeakingAttemptHistoryEntryDto>> getSpeakingPracticeHistory(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/speaking/history/{userId}", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<SpeakingAttemptHistoryEntryDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch speaking practice history for userId={}", userId, ex));
	}

	/** Fetches full detail for one of a learner's own past speaking-practice attempts. */
	public Mono<SpeakingAttemptDetailDto> getSpeakingAttemptDetail(String userId, Long attemptId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/speaking/history/{userId}/{attemptId}", userId, attemptId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<SpeakingAttemptDetailDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch speaking attempt detail for userId={}, attemptId={}", userId, attemptId, ex));
	}

	/** Fetches every listening-library catalog topic with this learner's own progression status (bootstraps the first topic to UNLOCKED). */
	public Mono<List<ListeningLibraryTopicDto>> getListeningLibraryTopics(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/listening/library/{userId}/topics", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<ListeningLibraryTopicDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to list listening library topics for userId={}", userId, ex));
	}

	/** Starts a new listening-library Section for a topic, or resumes its most recent one (topic must be UNLOCKED or IN_PROGRESS). */
	public Mono<ListeningLibrarySectionDto> startListeningLibrarySection(String userId, Long topicId) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/listening/library/{userId}/topics/{topicId}/sections", userId, topicId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<ListeningLibrarySectionDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to start listening library section for userId={}, topicId={}", userId, topicId, ex));
	}

	/** Scores a submitted answer set for one listening-library section; passes the topic and unlocks the next one on pass. */
	public Mono<SubmitListeningAnswersResponse> submitListeningLibraryAnswers(
			String userId, Long sectionId, SubmitListeningAnswersRequest request) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/listening/library/{userId}/sections/{sectionId}/answers", userId, sectionId)
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<SubmitListeningAnswersResponse>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to submit listening library answers for userId={}, sectionId={}", userId, sectionId, ex));
	}

	/** Fetches a learner's completed listening-library section attempts, across all topics. */
	public Mono<List<ListeningLibraryHistoryEntryDto>> getListeningLibraryHistory(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/listening/library/{userId}/sections/history", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<ListeningLibraryHistoryEntryDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to get listening library history for userId={}", userId, ex));
	}

	/** Fetches every speaking-library catalog topic with this learner's own progression status (bootstraps the first topic to UNLOCKED). */
	public Mono<List<SpeakingLibraryTopicDto>> getSpeakingLibraryTopics(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/speaking/library/{userId}/topics", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<SpeakingLibraryTopicDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to list speaking library topics for userId={}", userId, ex));
	}

	/** Starts a new speaking-library Section for a topic, or resumes its most recent one (topic must be UNLOCKED or IN_PROGRESS). */
	public Mono<SpeakingLibrarySectionDto> startSpeakingLibrarySection(String userId, Long topicId) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/speaking/library/{userId}/topics/{topicId}/sections", userId, topicId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<SpeakingLibrarySectionDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to start speaking library section for userId={}, topicId={}", userId, topicId, ex));
	}

	/** Streams a learner's recorded sentence attempt straight through to english-service's multipart submit endpoint, without buffering it in bff-service. */
	public Mono<SentenceAttemptResultDto> submitSpeakingSentenceAttempt(
			String userId, Long sectionId, Long sentenceId, FilePart audio) {
		MultipartBodyBuilder builder = new MultipartBodyBuilder();
		builder.asyncPart("audio", audio.content(), DataBuffer.class)
				.headers(headers -> headers.setContentDispositionFormData("audio", audio.filename()));

		return englishServiceClient.post()
				.uri("/api/v1/learn/speaking/library/{userId}/sections/{sectionId}/sentences/{sentenceId}/attempts",
						userId, sectionId, sentenceId)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(BodyInserters.fromMultipartData(builder.build()))
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<SentenceAttemptResultDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to submit speaking library sentence attempt for userId={}, sectionId={}, sentenceId={}",
						userId, sectionId, sentenceId, ex));
	}

	/** Finishes a speaking-library section: if every sentence has a passing attempt, marks the topic PASSED and unlocks the next one. */
	public Mono<FinishSpeakingSectionResponse> finishSpeakingLibrarySection(String userId, Long sectionId) {
		return englishServiceClient.post()
				.uri("/api/v1/learn/speaking/library/{userId}/sections/{sectionId}/finish", userId, sectionId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<FinishSpeakingSectionResponse>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to finish speaking library section for userId={}, sectionId={}", userId, sectionId, ex));
	}

	/** Fetches a learner's scored speaking-library sentence attempts, across all topics. */
	public Mono<List<SpeakingLibraryHistoryEntryDto>> getSpeakingLibraryHistory(String userId) {
		return englishServiceClient.get()
				.uri("/api/v1/learn/speaking/library/{userId}/sections/history", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<List<SpeakingLibraryHistoryEntryDto>>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to get speaking library history for userId={}", userId, ex));
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
