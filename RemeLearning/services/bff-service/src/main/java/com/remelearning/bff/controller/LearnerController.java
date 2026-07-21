package com.remelearning.bff.controller;

import com.remelearning.bff.client.EnglishServiceClient;
import com.remelearning.bff.client.RecommendationServiceClient;
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
import com.remelearning.bff.dto.LearnerOverviewResponse;
import com.remelearning.bff.dto.PracticeRedoRequestDto;
import com.remelearning.bff.dto.RecommendationDto;
import com.remelearning.bff.dto.StartDictationSessionRequestDto;
import com.remelearning.bff.dto.WeakPointDto;
import com.remelearning.bff.service.LearnerOverviewService;
import com.remelearning.bff.service.WeakPointAggregationService;
import com.remelearning.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Tag(name = "Learners", description = "Composite views for the UI, aggregated from the domain services")
@RestController
@RequestMapping("/api/v1/learners")
@RequiredArgsConstructor
public class LearnerController {

	private final LearnerOverviewService learnerOverviewService;
	private final WeakPointAggregationService weakPointAggregationService;
	private final RecommendationServiceClient recommendationServiceClient;
	private final EnglishServiceClient englishServiceClient;

	@Operation(summary = "Composite learner overview: dashboard progress/recommendations + recent recordings, fanned out in parallel")
	@GetMapping("/{userId}/overview")
	public Mono<ApiResponse<LearnerOverviewResponse>> getOverview(@PathVariable String userId) {
		return learnerOverviewService.getOverview(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "All of a learner's weak points, merged from english-service's vocabulary/grammar/pronunciation endpoints, keyed by category")
	@GetMapping("/{userId}/weak-points")
	public Mono<ApiResponse<Map<String, List<WeakPointDto>>>> getWeakPoints(@PathVariable String userId) {
		return weakPointAggregationService.getWeakPoints(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "A learner's recommendations grouped by category; thin proxy to recommendation-service")
	@GetMapping("/{userId}/recommendations")
	public Mono<ApiResponse<Map<String, List<RecommendationDto>>>> getRecommendations(@PathVariable String userId) {
		return recommendationServiceClient.getGroupedRecommendations(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "The redo-exercise set: a learner's top weak items across all three domains, "
			+ "sorted by forgetting score desc, to present as the next exercise to redo")
	@GetMapping("/{userId}/practice/next")
	public Mono<ApiResponse<List<WeakPointDto>>> getNextPracticeSet(
			@PathVariable String userId,
			@RequestParam(required = false, defaultValue = "10") int limit) {
		return weakPointAggregationService.getNextPracticeSet(userId, limit).map(ApiResponse::ok);
	}

	@Operation(summary = "Submit the graded results of a learner redoing an exercise; thin proxy to "
			+ "english-service, which refreshes mistake history and re-triggers forgetting-score analysis")
	@PostMapping("/{userId}/practice/redo")
	public Mono<ApiResponse<Void>> redoPractice(@PathVariable String userId, @RequestBody PracticeRedoRequestDto request) {
		request.setUserId(userId);
		return englishServiceClient.redoPractice(request);
	}

	@Operation(summary = "The dictation library's filter facets (skill/level/topic/exam-type); thin proxy to english-service")
	@GetMapping("/{userId}/dictation/facets")
	public Mono<ApiResponse<DictationFacetsDto>> getDictationFacets(@PathVariable String userId) {
		return englishServiceClient.getDictationFacets().map(ApiResponse::ok);
	}

	@Operation(summary = "Browse library clips filtered by skill/level/topic/exam-type; thin proxy to english-service")
	@GetMapping("/{userId}/dictation/clips")
	public Mono<ApiResponse<List<DictationClipDto>>> listDictationClips(
			@PathVariable String userId,
			@RequestParam(required = false) String skill,
			@RequestParam(required = false) String level,
			@RequestParam(required = false) String topic,
			@RequestParam(required = false) String examType,
			@RequestParam(required = false, defaultValue = "50") int limit) {
		return englishServiceClient.listDictationClips(skill, level, topic, examType, limit).map(ApiResponse::ok);
	}

	@Operation(summary = "Stream one library clip's audio; relays english-service's audio response")
	@GetMapping("/{userId}/dictation/clips/{clipId}/audio")
	public Mono<ResponseEntity<Flux<DataBuffer>>> getDictationClipAudio(
			@PathVariable String userId, @PathVariable Long clipId) {
		return englishServiceClient.streamClipAudio(clipId);
	}

	@Operation(summary = "The dictation library's folders (topic groupings), each with its lesson count; thin proxy to english-service")
	@GetMapping("/{userId}/dictation/folders")
	public Mono<ApiResponse<List<DictationFolderDto>>> getDictationFolders(@PathVariable String userId) {
		return englishServiceClient.getDictationFolders().map(ApiResponse::ok);
	}

	@Operation(summary = "The lessons (clips) inside one dictation folder, light-weight (no script); thin proxy to english-service")
	@GetMapping("/{userId}/dictation/folders/{folderId}/lessons")
	public Mono<ApiResponse<List<DictationLessonSummaryDto>>> getDictationFolderLessons(
			@PathVariable String userId, @PathVariable String folderId) {
		return englishServiceClient.getDictationFolderLessons(folderId).map(ApiResponse::ok);
	}

	@Operation(summary = "Full detail for one dictation clip - script + split sentences, optionally translated - for sentence-by-sentence practice; thin proxy to english-service")
	@GetMapping("/{userId}/dictation/clips/{clipId}")
	public Mono<ApiResponse<DictationClipDetailDto>> getDictationClip(
			@PathVariable String userId, @PathVariable Long clipId,
			@RequestParam(required = false) String translationLang) {
		return englishServiceClient.getDictationClip(clipId, translationLang).map(ApiResponse::ok);
	}

	@Operation(summary = "Start a dictation session: a batch of library clips matching the requested facets; thin proxy to english-service")
	@PostMapping("/{userId}/dictation/sessions")
	public Mono<ApiResponse<List<DictationClipDto>>> startDictationSession(
			@PathVariable String userId, @RequestBody StartDictationSessionRequestDto request) {
		return englishServiceClient.startDictationSession(userId, request).map(ApiResponse::ok);
	}

	@Operation(summary = "Grade a learner's typed transcript for one dictation clip; thin proxy to english-service")
	@PostMapping("/{userId}/dictation/attempts")
	public Mono<ApiResponse<DictationAttemptResultDto>> submitDictationAttempt(
			@PathVariable String userId, @RequestBody DictationAttemptRequestDto request) {
		request.setUserId(userId);
		return englishServiceClient.submitDictationAttempt(request).map(ApiResponse::ok);
	}

	@Operation(summary = "A learner's past dictation attempts, newest first; thin proxy to english-service")
	@GetMapping("/{userId}/dictation/history")
	public Mono<ApiResponse<List<DictationHistoryEntryDto>>> getDictationHistory(@PathVariable String userId) {
		return englishServiceClient.getDictationHistory(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "Full detail for one of a learner's own past dictation attempts; thin proxy to english-service")
	@GetMapping("/{userId}/dictation/history/{attemptId}")
	public Mono<ApiResponse<DictationAttemptDetailDto>> getDictationAttemptDetail(
			@PathVariable String userId, @PathVariable Long attemptId) {
		return englishServiceClient.getDictationAttemptDetail(userId, attemptId).map(ApiResponse::ok);
	}

	@Operation(summary = "A learner's AI-practice items (Gemini + Supertonic); thin proxy to english-service")
	@GetMapping("/{userId}/dictation/ai-practice")
	public Mono<ApiResponse<List<DictationPracticeItemDto>>> getAiPractice(@PathVariable String userId) {
		return englishServiceClient.getAiPractice(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "Generate AI-practice audio honoring the requested level/exam-type facets (concrete value, \"RANDOM\", or omitted) and translation language; thin proxy to english-service")
	@PostMapping("/{userId}/dictation/ai-practice/generate")
	public Mono<ApiResponse<List<DictationPracticeItemDto>>> generateAiPractice(
			@PathVariable String userId, @RequestBody(required = false) GenerateAiPracticeRequestDto request) {
		return englishServiceClient.generateAiPractice(userId, request == null ? new GenerateAiPracticeRequestDto() : request)
				.map(ApiResponse::ok);
	}

	@Operation(summary = "Generate AI-practice audio targeted at one specific past attempt's mistakes (the \"Luyện tập với AI\" history action); thin proxy to english-service")
	@PostMapping("/{userId}/dictation/history/{attemptId}/ai-practice")
	public Mono<ApiResponse<List<DictationPracticeItemDto>>> generateAiPracticeFromAttempt(
			@PathVariable String userId, @PathVariable Long attemptId,
			@RequestParam(required = false) String translationLang) {
		return englishServiceClient.generateAiPracticeFromAttempt(userId, attemptId, translationLang).map(ApiResponse::ok);
	}

	@Operation(summary = "Stream one AI-practice item's synthesized audio; relays english-service's audio response")
	@GetMapping("/{userId}/dictation/ai-practice/items/{practiceItemId}/audio")
	public Mono<ResponseEntity<Flux<DataBuffer>>> getAiPracticeAudio(
			@PathVariable String userId, @PathVariable Long practiceItemId) {
		return englishServiceClient.streamPracticeAudio(practiceItemId);
	}

	@Operation(summary = "Full detail for one AI-practice item - passage text split into sentences - for sentence-by-sentence practice; thin proxy to english-service")
	@GetMapping("/{userId}/dictation/ai-practice/items/{practiceItemId}/detail")
	public Mono<ApiResponse<DictationPracticeItemDetailDto>> getAiPracticeDetail(
			@PathVariable String userId, @PathVariable Long practiceItemId) {
		return englishServiceClient.getAiPracticeDetail(practiceItemId).map(ApiResponse::ok);
	}
}
