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
import com.remelearning.bff.dto.GenerateGrammarPracticeRequestDto;
import com.remelearning.bff.dto.GenerateVocabPracticeRequestDto;
import com.remelearning.bff.dto.GrammarAttemptDetailDto;
import com.remelearning.bff.dto.GrammarAttemptHistoryEntryDto;
import com.remelearning.bff.dto.GrammarAttemptResultDto;
import com.remelearning.bff.dto.GrammarPracticeItemDto;
import com.remelearning.bff.dto.GenerateListeningPracticeRequestDto;
import com.remelearning.bff.dto.LearnerOverviewResponse;
import com.remelearning.bff.dto.ListeningAttemptDetailDto;
import com.remelearning.bff.dto.ListeningAttemptHistoryEntryDto;
import com.remelearning.bff.dto.ListeningAttemptResultDto;
import com.remelearning.bff.dto.ListeningPracticeItemDto;
import com.remelearning.bff.dto.PracticeRedoRequestDto;
import com.remelearning.bff.dto.RecommendationDto;
import com.remelearning.bff.dto.SectionAnswerResultDto;
import com.remelearning.bff.dto.SectionCardDto;
import com.remelearning.bff.dto.SectionHistoryEntryDto;
import com.remelearning.bff.dto.StartDictationSessionRequestDto;
import com.remelearning.bff.dto.StartSectionRequestDto;
import com.remelearning.bff.dto.GenerateSpeakingPracticeRequestDto;
import com.remelearning.bff.dto.SpeakingAttemptDetailDto;
import com.remelearning.bff.dto.SpeakingAttemptHistoryEntryDto;
import com.remelearning.bff.dto.SpeakingAttemptResultDto;
import com.remelearning.bff.dto.SpeakingPracticeItemDto;
import com.remelearning.bff.dto.SubmitGrammarAttemptRequestDto;
import com.remelearning.bff.dto.SubmitListeningAttemptRequestDto;
import com.remelearning.bff.dto.SubmitSectionAnswerRequestDto;
import com.remelearning.bff.dto.SubmitVocabAttemptRequestDto;
import com.remelearning.bff.dto.TopicSummaryDto;
import com.remelearning.bff.dto.VocabAttemptDetailDto;
import com.remelearning.bff.dto.VocabAttemptHistoryEntryDto;
import com.remelearning.bff.dto.VocabAttemptResultDto;
import com.remelearning.bff.dto.VocabPracticeItemDto;
import com.remelearning.bff.dto.WeakPointDto;
import com.remelearning.bff.service.LearnerOverviewService;
import com.remelearning.bff.service.WeakPointAggregationService;
import com.remelearning.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
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

	@Operation(summary = "The lessons (clips) inside one dictation folder, light-weight (no script), joined with the learner's own progress on each; thin proxy to english-service")
	@GetMapping("/{userId}/dictation/folders/{folderId}/lessons")
	public Mono<ApiResponse<List<DictationLessonSummaryDto>>> getDictationFolderLessons(
			@PathVariable String userId, @PathVariable String folderId) {
		return englishServiceClient.getDictationFolderLessons(folderId, userId).map(ApiResponse::ok);
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

	@Operation(summary = "Generate one AI vocabulary practice set, targeting the given focus words or (if omitted) the learner's own top weak points; thin proxy to english-service")
	@PostMapping("/{userId}/learn/vocabulary/generate")
	public Mono<ApiResponse<VocabPracticeItemDto>> generateVocabPractice(
			@PathVariable String userId, @RequestBody(required = false) GenerateVocabPracticeRequestDto request) {
		return englishServiceClient.generateVocabPractice(userId, request == null ? new GenerateVocabPracticeRequestDto() : request)
				.map(ApiResponse::ok);
	}

	@Operation(summary = "A learner's generated vocabulary practice sets, newest first; thin proxy to english-service")
	@GetMapping("/{userId}/learn/vocabulary/items")
	public Mono<ApiResponse<List<VocabPracticeItemDto>>> listVocabPracticeItems(@PathVariable String userId) {
		return englishServiceClient.listVocabPracticeItems(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "Full detail (questions, no answers) for one vocabulary practice set; thin proxy to english-service")
	@GetMapping("/{userId}/learn/vocabulary/items/{itemId}")
	public Mono<ApiResponse<VocabPracticeItemDto>> getVocabPracticeItem(
			@PathVariable String userId, @PathVariable Long itemId) {
		return englishServiceClient.getVocabPracticeItem(itemId).map(ApiResponse::ok);
	}

	@Operation(summary = "Grade a submitted vocabulary-practice attempt; thin proxy to english-service")
	@PostMapping("/{userId}/learn/vocabulary/attempts")
	public Mono<ApiResponse<VocabAttemptResultDto>> submitVocabAttempt(
			@PathVariable String userId, @RequestBody SubmitVocabAttemptRequestDto request) {
		request.setUserId(userId);
		return englishServiceClient.submitVocabAttempt(request).map(ApiResponse::ok);
	}

	@Operation(summary = "A learner's past vocabulary-practice attempts, newest first; thin proxy to english-service")
	@GetMapping("/{userId}/learn/vocabulary/history")
	public Mono<ApiResponse<List<VocabAttemptHistoryEntryDto>>> getVocabPracticeHistory(@PathVariable String userId) {
		return englishServiceClient.getVocabPracticeHistory(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "Full detail for one of a learner's own past vocabulary-practice attempts; thin proxy to english-service")
	@GetMapping("/{userId}/learn/vocabulary/history/{attemptId}")
	public Mono<ApiResponse<VocabAttemptDetailDto>> getVocabAttemptDetail(
			@PathVariable String userId, @PathVariable Long attemptId) {
		return englishServiceClient.getVocabAttemptDetail(userId, attemptId).map(ApiResponse::ok);
	}

	@Operation(summary = "List every vocabulary-library topic with word count and mastered-word count; thin proxy to english-service")
	@GetMapping("/{userId}/learn/vocabulary/library/topics")
	public Mono<ApiResponse<List<TopicSummaryDto>>> listVocabLibraryTopics(@PathVariable String userId) {
		return englishServiceClient.listVocabLibraryTopics(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "Start a new vocabulary-library Section for a topic; thin proxy to english-service")
	@PostMapping("/{userId}/learn/vocabulary/library/topics/{topicId}/sections")
	public Mono<ApiResponse<SectionCardDto>> startVocabSection(
			@PathVariable String userId, @PathVariable Long topicId, @RequestBody(required = false) StartSectionRequestDto request) {
		return englishServiceClient.startVocabSection(userId, topicId, request == null ? new StartSectionRequestDto() : request)
				.map(ApiResponse::ok);
	}

	@Operation(summary = "Grade the current Section card's answer and get the next card; thin proxy to english-service")
	@PostMapping("/{userId}/learn/vocabulary/library/sections/{sectionId}/answers")
	public Mono<ApiResponse<SectionAnswerResultDto>> submitVocabSectionAnswer(
			@PathVariable String userId, @PathVariable Long sectionId, @RequestBody(required = false) SubmitSectionAnswerRequestDto request) {
		return englishServiceClient.submitVocabSectionAnswer(sectionId, request == null ? new SubmitSectionAnswerRequestDto() : request)
				.map(ApiResponse::ok);
	}

	@Operation(summary = "End a vocabulary-library Section early; thin proxy to english-service")
	@PostMapping("/{userId}/learn/vocabulary/library/sections/{sectionId}/finish")
	public Mono<ApiResponse<SectionAnswerResultDto>> finishVocabSection(@PathVariable String userId, @PathVariable Long sectionId) {
		return englishServiceClient.finishVocabSection(sectionId).map(ApiResponse::ok);
	}

	@Operation(summary = "A learner's finished vocabulary-library Sections, newest first; thin proxy to english-service")
	@GetMapping("/{userId}/learn/vocabulary/library/sections/history")
	public Mono<ApiResponse<List<SectionHistoryEntryDto>>> getVocabSectionHistory(@PathVariable String userId) {
		return englishServiceClient.getVocabSectionHistory(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "Stream one vocabulary-library word's synthesized audio; relays english-service's audio response")
	@GetMapping("/{userId}/learn/vocabulary/library/words/{wordId}/audio")
	public Mono<ResponseEntity<Flux<DataBuffer>>> getVocabLibraryWordAudio(@PathVariable String userId, @PathVariable Long wordId) {
		return englishServiceClient.streamVocabLibraryWordAudio(wordId);
	}

	@Operation(summary = "Generate one AI grammar practice set, targeting the given focus rules or (if omitted) the learner's own top weak points; thin proxy to english-service")
	@PostMapping("/{userId}/learn/grammar/generate")
	public Mono<ApiResponse<GrammarPracticeItemDto>> generateGrammarPractice(
			@PathVariable String userId, @RequestBody(required = false) GenerateGrammarPracticeRequestDto request) {
		return englishServiceClient.generateGrammarPractice(userId, request == null ? new GenerateGrammarPracticeRequestDto() : request)
				.map(ApiResponse::ok);
	}

	@Operation(summary = "A learner's generated grammar practice sets, newest first; thin proxy to english-service")
	@GetMapping("/{userId}/learn/grammar/items")
	public Mono<ApiResponse<List<GrammarPracticeItemDto>>> listGrammarPracticeItems(@PathVariable String userId) {
		return englishServiceClient.listGrammarPracticeItems(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "Full detail (questions, no answers) for one grammar practice set; thin proxy to english-service")
	@GetMapping("/{userId}/learn/grammar/items/{itemId}")
	public Mono<ApiResponse<GrammarPracticeItemDto>> getGrammarPracticeItem(
			@PathVariable String userId, @PathVariable Long itemId) {
		return englishServiceClient.getGrammarPracticeItem(itemId).map(ApiResponse::ok);
	}

	@Operation(summary = "Grade a submitted grammar-practice attempt; thin proxy to english-service")
	@PostMapping("/{userId}/learn/grammar/attempts")
	public Mono<ApiResponse<GrammarAttemptResultDto>> submitGrammarAttempt(
			@PathVariable String userId, @RequestBody SubmitGrammarAttemptRequestDto request) {
		request.setUserId(userId);
		return englishServiceClient.submitGrammarAttempt(request).map(ApiResponse::ok);
	}

	@Operation(summary = "A learner's past grammar-practice attempts, newest first; thin proxy to english-service")
	@GetMapping("/{userId}/learn/grammar/history")
	public Mono<ApiResponse<List<GrammarAttemptHistoryEntryDto>>> getGrammarPracticeHistory(@PathVariable String userId) {
		return englishServiceClient.getGrammarPracticeHistory(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "Full detail for one of a learner's own past grammar-practice attempts; thin proxy to english-service")
	@GetMapping("/{userId}/learn/grammar/history/{attemptId}")
	public Mono<ApiResponse<GrammarAttemptDetailDto>> getGrammarAttemptDetail(
			@PathVariable String userId, @PathVariable Long attemptId) {
		return englishServiceClient.getGrammarAttemptDetail(userId, attemptId).map(ApiResponse::ok);
	}

	@Operation(summary = "Generate one AI listening passage, targeting the given focus keywords or (if omitted) the learner's own recently-missed keywords; thin proxy to english-service")
	@PostMapping("/{userId}/learn/listening/generate")
	public Mono<ApiResponse<ListeningPracticeItemDto>> generateListeningPractice(
			@PathVariable String userId, @RequestBody(required = false) GenerateListeningPracticeRequestDto request) {
		return englishServiceClient.generateListeningPractice(userId, request == null ? new GenerateListeningPracticeRequestDto() : request)
				.map(ApiResponse::ok);
	}

	@Operation(summary = "A learner's generated listening practice items, newest first; thin proxy to english-service")
	@GetMapping("/{userId}/learn/listening/items")
	public Mono<ApiResponse<List<ListeningPracticeItemDto>>> listListeningPracticeItems(@PathVariable String userId) {
		return englishServiceClient.listListeningPracticeItems(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "Full detail (questions, no transcript/answers) for one listening practice item; thin proxy to english-service")
	@GetMapping("/{userId}/learn/listening/items/{itemId}")
	public Mono<ApiResponse<ListeningPracticeItemDto>> getListeningPracticeItem(
			@PathVariable String userId, @PathVariable Long itemId) {
		return englishServiceClient.getListeningPracticeItem(itemId).map(ApiResponse::ok);
	}

	@Operation(summary = "Stream one listening practice item's synthesized audio; relays english-service's audio response")
	@GetMapping("/{userId}/learn/listening/items/{itemId}/audio")
	public Mono<ResponseEntity<Flux<DataBuffer>>> getListeningAudio(
			@PathVariable String userId, @PathVariable Long itemId) {
		return englishServiceClient.streamListeningAudio(itemId);
	}

	@Operation(summary = "Grade a submitted listening-practice attempt; thin proxy to english-service")
	@PostMapping("/{userId}/learn/listening/attempts")
	public Mono<ApiResponse<ListeningAttemptResultDto>> submitListeningAttempt(
			@PathVariable String userId, @RequestBody SubmitListeningAttemptRequestDto request) {
		request.setUserId(userId);
		return englishServiceClient.submitListeningAttempt(request).map(ApiResponse::ok);
	}

	@Operation(summary = "A learner's past listening-practice attempts, newest first; thin proxy to english-service")
	@GetMapping("/{userId}/learn/listening/history")
	public Mono<ApiResponse<List<ListeningAttemptHistoryEntryDto>>> getListeningPracticeHistory(@PathVariable String userId) {
		return englishServiceClient.getListeningPracticeHistory(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "Full detail for one of a learner's own past listening-practice attempts; thin proxy to english-service")
	@GetMapping("/{userId}/learn/listening/history/{attemptId}")
	public Mono<ApiResponse<ListeningAttemptDetailDto>> getListeningAttemptDetail(
			@PathVariable String userId, @PathVariable Long attemptId) {
		return englishServiceClient.getListeningAttemptDetail(userId, attemptId).map(ApiResponse::ok);
	}

	@Operation(summary = "Generate one AI speaking-practice sentence with a Supertonic sample recording, targeting the given focus words or (if omitted) the learner's own top pronunciation weak points; thin proxy to english-service")
	@PostMapping("/{userId}/learn/speaking/generate")
	public Mono<ApiResponse<SpeakingPracticeItemDto>> generateSpeakingPractice(
			@PathVariable String userId, @RequestBody(required = false) GenerateSpeakingPracticeRequestDto request) {
		return englishServiceClient.generateSpeakingPractice(userId, request == null ? new GenerateSpeakingPracticeRequestDto() : request)
				.map(ApiResponse::ok);
	}

	@Operation(summary = "A learner's generated speaking practice items, newest first; thin proxy to english-service")
	@GetMapping("/{userId}/learn/speaking/items")
	public Mono<ApiResponse<List<SpeakingPracticeItemDto>>> listSpeakingPracticeItems(@PathVariable String userId) {
		return englishServiceClient.listSpeakingPracticeItems(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "Full detail (target text + sample audio URL) for one speaking practice item; thin proxy to english-service")
	@GetMapping("/{userId}/learn/speaking/items/{itemId}")
	public Mono<ApiResponse<SpeakingPracticeItemDto>> getSpeakingPracticeItem(
			@PathVariable String userId, @PathVariable Long itemId) {
		return englishServiceClient.getSpeakingPracticeItem(itemId).map(ApiResponse::ok);
	}

	@Operation(summary = "Stream one speaking practice item's Supertonic sample audio; relays english-service's audio response")
	@GetMapping("/{userId}/learn/speaking/items/{itemId}/sample-audio")
	public Mono<ResponseEntity<Flux<DataBuffer>>> getSpeakingSampleAudio(
			@PathVariable String userId, @PathVariable Long itemId) {
		return englishServiceClient.streamSpeakingSampleAudio(itemId);
	}

	@Operation(summary = "Submit a learner's recorded speaking attempt; streamed straight through to english-service without buffering the file in bff-service, scored via ai-service's wav2vec2 GOP model")
	@PostMapping(value = "/{userId}/learn/speaking/attempts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Mono<ApiResponse<SpeakingAttemptResultDto>> submitSpeakingAttempt(
			@PathVariable String userId,
			@RequestPart("audio") FilePart audio,
			@RequestPart("practiceItemId") String practiceItemId) {
		return englishServiceClient.submitSpeakingAttempt(userId, Long.valueOf(practiceItemId), audio).map(ApiResponse::ok);
	}

	@Operation(summary = "A learner's past speaking-practice attempts, newest first; thin proxy to english-service")
	@GetMapping("/{userId}/learn/speaking/history")
	public Mono<ApiResponse<List<SpeakingAttemptHistoryEntryDto>>> getSpeakingPracticeHistory(@PathVariable String userId) {
		return englishServiceClient.getSpeakingPracticeHistory(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "Full detail for one of a learner's own past speaking-practice attempts; thin proxy to english-service")
	@GetMapping("/{userId}/learn/speaking/history/{attemptId}")
	public Mono<ApiResponse<SpeakingAttemptDetailDto>> getSpeakingAttemptDetail(
			@PathVariable String userId, @PathVariable Long attemptId) {
		return englishServiceClient.getSpeakingAttemptDetail(userId, attemptId).map(ApiResponse::ok);
	}
}
