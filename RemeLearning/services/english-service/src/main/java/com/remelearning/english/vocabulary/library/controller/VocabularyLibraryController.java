package com.remelearning.english.vocabulary.library.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.vocabulary.library.dto.SectionAnswerResultDto;
import com.remelearning.english.vocabulary.library.dto.SectionCardDto;
import com.remelearning.english.vocabulary.library.dto.SectionHistoryEntryDto;
import com.remelearning.english.vocabulary.library.dto.StartSectionRequest;
import com.remelearning.english.vocabulary.library.dto.SubmitSectionAnswerRequest;
import com.remelearning.english.vocabulary.library.dto.TopicSummaryDto;
import com.remelearning.english.vocabulary.library.dto.VocabularyAudioResource;
import com.remelearning.english.vocabulary.library.service.VocabularyLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Vocabulary Library", description = "Topic-organized vocabulary word bank and Leitner-lite Section practice")
@RestController
@RequestMapping("/api/v1/learn/vocabulary/library")
@RequiredArgsConstructor
public class VocabularyLibraryController {

	private final VocabularyLibraryService vocabularyLibraryService;

	@Operation(summary = "List every topic with its word count and this learner's mastered-word count")
	@GetMapping("/{userId}/topics")
	public ApiResponse<List<TopicSummaryDto>> listTopics(@PathVariable String userId) {
		return ApiResponse.ok(vocabularyLibraryService.listTopics(userId));
	}

	@Operation(summary = "Start a new Section for a topic (topping up its word bank via the LLM first if needed); returns the first card")
	@PostMapping("/{userId}/topics/{topicId}/sections")
	public ApiResponse<SectionCardDto> startSection(
			@PathVariable String userId, @PathVariable Long topicId, @RequestBody(required = false) StartSectionRequest request) {
		return ApiResponse.ok(vocabularyLibraryService.startSection(userId, topicId, request == null ? new StartSectionRequest() : request));
	}

	@Operation(summary = "Grade the current card's answer (or acknowledge an INTRO) and get the next card, or a completed result")
	@PostMapping("/sections/{sectionId}/answers")
	public ApiResponse<SectionAnswerResultDto> submitAnswer(
			@PathVariable Long sectionId, @RequestBody(required = false) SubmitSectionAnswerRequest request) {
		return ApiResponse.ok(vocabularyLibraryService.submitAnswer(sectionId, request == null ? new SubmitSectionAnswerRequest() : request));
	}

	@Operation(summary = "End an in-progress Section early; whatever was answered still feeds the weak-point pipeline")
	@PostMapping("/sections/{sectionId}/finish")
	public ApiResponse<SectionAnswerResultDto> finishSection(@PathVariable Long sectionId) {
		return ApiResponse.ok(vocabularyLibraryService.finishSection(sectionId));
	}

	@Operation(summary = "A learner's finished Sections, newest first")
	@GetMapping("/{userId}/sections/history")
	public ApiResponse<List<SectionHistoryEntryDto>> getSectionHistory(@PathVariable String userId) {
		return ApiResponse.ok(vocabularyLibraryService.getSectionHistory(userId));
	}

	@Operation(summary = "Stream one library word's synthesized pronunciation audio")
	@GetMapping("/words/{wordId}/audio")
	public ResponseEntity<InputStreamResource> getWordAudio(@PathVariable Long wordId) {
		VocabularyAudioResource audio = vocabularyLibraryService.loadWordAudio(wordId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(audio.mimeType()))
				.contentLength(audio.size())
				.header("Content-Disposition", ContentDisposition.inline().filename(audio.filename()).build().toString())
				.body(new InputStreamResource(audio.stream()));
	}
}
