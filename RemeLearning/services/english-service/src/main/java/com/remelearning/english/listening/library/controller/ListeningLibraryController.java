package com.remelearning.english.listening.library.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.listening.dto.ListeningAudioResource;
import com.remelearning.english.listening.dto.ListeningPracticeItemDto;
import com.remelearning.english.listening.library.domain.ListeningLibraryAttempt;
import com.remelearning.english.listening.library.dto.ListeningLibrarySectionDto;
import com.remelearning.english.listening.library.dto.ListeningLibraryTopicDto;
import com.remelearning.english.listening.library.dto.SubmitListeningAnswersRequest;
import com.remelearning.english.listening.library.dto.SubmitListeningAnswersResponse;
import com.remelearning.english.listening.library.service.ListeningLibraryService;
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

@Tag(name = "Listening Library", description = "Fixed listening-topic catalog with AI-generated passage+audio Sections and pass/unlock-next-topic progression")
@RestController
@RequestMapping("/api/v1/learn/listening/library")
@RequiredArgsConstructor
public class ListeningLibraryController {

	private final ListeningLibraryService listeningLibraryService;

	@Operation(summary = "List every catalog topic with this learner's own progression status (bootstraps the first topic to UNLOCKED)")
	@GetMapping("/{userId}/topics")
	public ApiResponse<List<ListeningLibraryTopicDto>> getTopics(@PathVariable String userId) {
		return ApiResponse.ok(listeningLibraryService.getTopics(userId));
	}

	@Operation(summary = "Start a new Section for a topic, or resume its most recent one (must be UNLOCKED or IN_PROGRESS)")
	@PostMapping("/{userId}/topics/{topicId}/sections")
	public ApiResponse<ListeningLibrarySectionDto> startOrResumeSection(
			@PathVariable String userId, @PathVariable Long topicId) {
		return ApiResponse.ok(listeningLibraryService.startOrResumeSection(userId, topicId));
	}

	@Operation(summary = "Stream one section's synthesized audio")
	@GetMapping("/sections/{sectionId}/audio")
	public ResponseEntity<InputStreamResource> getSectionAudio(@PathVariable Long sectionId) {
		ListeningAudioResource audio = listeningLibraryService.loadSectionAudio(sectionId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(audio.contentType()))
				.contentLength(audio.contentLength())
				.header("Content-Disposition", ContentDisposition.inline().filename(audio.filename()).build().toString())
				.body(new InputStreamResource(audio.stream()));
	}

	@Operation(summary = "Score a submitted answer set for one section; passes the topic and unlocks the next one on pass")
	@PostMapping("/{userId}/sections/{sectionId}/answers")
	public ApiResponse<SubmitListeningAnswersResponse> submitAnswers(
			@PathVariable String userId, @PathVariable Long sectionId,
			@RequestBody SubmitListeningAnswersRequest request) {
		return ApiResponse.ok(listeningLibraryService.submitAnswers(userId, sectionId, request));
	}

	@Operation(summary = "This learner's completed section attempts, across all topics")
	@GetMapping("/{userId}/sections/history")
	public ApiResponse<List<ListeningLibraryAttempt>> getHistory(@PathVariable String userId) {
		return ApiResponse.ok(listeningLibraryService.getHistory(userId));
	}

	@Operation(summary = "Generate AI practice targeted at this learner's own most recent attempt's missed questions on one section (the \"Luyện tập với AI\" action) - persists into the same listening_practice_items bank the learn flow uses")
	@PostMapping("/{userId}/sections/{sectionId}/ai-practice")
	public ApiResponse<List<ListeningPracticeItemDto>> generateFromSection(
			@PathVariable String userId, @PathVariable Long sectionId) {
		return ApiResponse.ok(listeningLibraryService.generatePracticeFromSection(userId, sectionId));
	}
}
