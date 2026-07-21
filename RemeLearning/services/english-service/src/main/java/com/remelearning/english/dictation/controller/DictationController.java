package com.remelearning.english.dictation.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.dictation.dto.DictationAttemptDetailDto;
import com.remelearning.english.dictation.dto.DictationAttemptRequest;
import com.remelearning.english.dictation.dto.DictationAttemptResultDto;
import com.remelearning.english.dictation.dto.DictationAudioResource;
import com.remelearning.english.dictation.dto.DictationClipDetailDto;
import com.remelearning.english.dictation.dto.DictationClipDto;
import com.remelearning.english.dictation.dto.DictationFacetsDto;
import com.remelearning.english.dictation.dto.DictationFolderDto;
import com.remelearning.english.dictation.dto.DictationHistoryEntryDto;
import com.remelearning.english.dictation.dto.DictationLessonSummaryDto;
import com.remelearning.english.dictation.dto.DictationPracticeItemDetailDto;
import com.remelearning.english.dictation.dto.DictationPracticeItemDto;
import com.remelearning.english.dictation.dto.GenerateAiPracticeRequest;
import com.remelearning.english.dictation.dto.StartDictationSessionRequest;
import com.remelearning.english.dictation.service.DictationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Dictation", description = "Listen-and-type dictation over a fixed real-audio library plus an AI (Gemini + Supertonic) practice section")
@RestController
@RequestMapping("/api/v1/dictation")
@RequiredArgsConstructor
public class DictationController {

	private final DictationService dictationService;

	@Operation(summary = "The distinct skill/level/topic/exam-type values available across the library, for the UI filters")
	@GetMapping("/facets")
	public ApiResponse<DictationFacetsDto> getFacets() {
		return ApiResponse.ok(dictationService.getFacets());
	}

	@Operation(summary = "Browse library clips, filtered by any subset of skill/level/topic/examType")
	@GetMapping("/clips")
	public ApiResponse<List<DictationClipDto>> listClips(
			@RequestParam(required = false) String skill,
			@RequestParam(required = false) String level,
			@RequestParam(required = false) String topic,
			@RequestParam(required = false) String examType,
			@RequestParam(required = false, defaultValue = "50") int limit) {
		return ApiResponse.ok(dictationService.listClips(skill, level, topic, examType, limit));
	}

	@Operation(summary = "The distinct clip folders (topic groupings), each with its lesson count, for folder->file browsing")
	@GetMapping("/folders")
	public ApiResponse<List<DictationFolderDto>> listFolders() {
		return ApiResponse.ok(dictationService.listFolders());
	}

	@Operation(summary = "The lessons (clips) inside one folder, light-weight (no script) - the answer only loads once a specific lesson is opened - joined with the learner's own progress (sentence count, attempt count, latest accuracy) on each")
	@GetMapping("/folders/{folderId}/lessons/{userId}")
	public ApiResponse<List<DictationLessonSummaryDto>> listFolderLessons(
			@PathVariable String folderId, @PathVariable String userId) {
		return ApiResponse.ok(dictationService.listFolderLessons(folderId, userId));
	}

	@Operation(summary = "Full detail for one clip - script + split sentences - for sentence-by-sentence practice, with an optional per-sentence translation")
	@GetMapping("/clips/{clipId}")
	public ApiResponse<DictationClipDetailDto> getClipDetail(
			@PathVariable Long clipId, @RequestParam(required = false) String translationLang) {
		return ApiResponse.ok(dictationService.getClipDetail(clipId, translationLang));
	}

	@Operation(summary = "Stream one library clip's audio")
	@GetMapping("/clips/{clipId}/audio")
	public ResponseEntity<InputStreamResource> getClipAudio(@PathVariable Long clipId) {
		return toAudioResponse(dictationService.loadClipAudio(clipId));
	}

	@Operation(summary = "Start a session: a batch of library clips matching the requested facets")
	@PostMapping("/sessions/{userId}")
	public ApiResponse<List<DictationClipDto>> startSession(
			@PathVariable String userId, @Valid @RequestBody StartDictationSessionRequest request) {
		return ApiResponse.ok(dictationService.startSession(userId, request));
	}

	@Operation(summary = "Grade a typed transcript (library or AI-practice clip); returns the word diff plus "
			+ "immediate AI suggestions and practice sentences, and feeds misses into the recommendation pipeline")
	@PostMapping("/attempts")
	public ApiResponse<DictationAttemptResultDto> submitAttempt(@Valid @RequestBody DictationAttemptRequest request) {
		return ApiResponse.ok(dictationService.submitAttempt(request));
	}

	@Operation(summary = "A learner's past dictation attempts, newest first")
	@GetMapping("/history/{userId}")
	public ApiResponse<List<DictationHistoryEntryDto>> getHistory(@PathVariable String userId) {
		return ApiResponse.ok(dictationService.getHistory(userId));
	}

	@Operation(summary = "Full detail for one of a learner's own past attempts - reference text, transcript, "
			+ "mistakes, and the AI suggestions generated at the time")
	@GetMapping("/history/{userId}/{attemptId}")
	public ApiResponse<DictationAttemptDetailDto> getAttemptDetail(
			@PathVariable String userId, @PathVariable Long attemptId) {
		return ApiResponse.ok(dictationService.getAttemptDetail(userId, attemptId));
	}

	@Operation(summary = "A learner's AI-practice items (audio URL present once synthesized)")
	@GetMapping("/ai-practice/{userId}")
	public ApiResponse<List<DictationPracticeItemDto>> getAiPractice(@PathVariable String userId) {
		return ApiResponse.ok(dictationService.getAiPractice(userId));
	}

	@Operation(summary = "Generate one AI-practice listening passage (Gemini monologue/dialogue, one random Supertonic voice per speaker, merged into one audio file) from the learner's still-unsynthesized items or most-missed words, honoring the requested level/exam-type facets (concrete value, \"RANDOM\", or omitted) and translation language")
	@PostMapping("/ai-practice/{userId}/generate")
	public ApiResponse<List<DictationPracticeItemDto>> generateAiPractice(
			@PathVariable String userId, @RequestBody(required = false) GenerateAiPracticeRequest request) {
		return ApiResponse.ok(dictationService.generateAiPractice(userId, request == null ? new GenerateAiPracticeRequest() : request));
	}

	@Operation(summary = "Generate AI-practice clips targeted at one specific past attempt's mistakes (the \"Luyện tập với AI\" action from a history row)")
	@PostMapping("/history/{userId}/{attemptId}/ai-practice")
	public ApiResponse<List<DictationPracticeItemDto>> generateAiPracticeFromAttempt(
			@PathVariable String userId, @PathVariable Long attemptId,
			@RequestParam(required = false) String translationLang) {
		return ApiResponse.ok(dictationService.generateAiPracticeFromAttempt(userId, attemptId, translationLang));
	}

	@Operation(summary = "Stream one AI-practice item's synthesized audio")
	@GetMapping("/ai-practice/items/{practiceItemId}/audio")
	public ResponseEntity<InputStreamResource> getPracticeAudio(@PathVariable Long practiceItemId) {
		return toAudioResponse(dictationService.loadPracticeAudio(practiceItemId));
	}

	@Operation(summary = "Full detail for one AI-practice item - passage text split into sentences - for sentence-by-sentence practice, mirroring a library clip's detail endpoint")
	@GetMapping("/ai-practice/items/{practiceItemId}/detail")
	public ApiResponse<DictationPracticeItemDetailDto> getAiPracticeDetail(@PathVariable Long practiceItemId) {
		return ApiResponse.ok(dictationService.getAiPracticeDetail(practiceItemId));
	}

	// Wraps a loaded audio stream into a streaming HTTP response with the right content type/length.
	private ResponseEntity<InputStreamResource> toAudioResponse(DictationAudioResource audio) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(audio.contentType()))
				.contentLength(audio.contentLength())
				.header("Content-Disposition", ContentDisposition.inline().filename(audio.filename()).build().toString())
				.body(new InputStreamResource(audio.stream()));
	}
}
