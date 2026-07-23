package com.remelearning.english.listening.library.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.listening.library.domain.ListeningLibraryAttempt;
import com.remelearning.english.listening.library.dto.ListeningLibrarySectionDto;
import com.remelearning.english.listening.library.dto.ListeningLibraryTopicDto;
import com.remelearning.english.listening.library.dto.SubmitListeningAnswersRequest;
import com.remelearning.english.listening.library.dto.SubmitListeningAnswersResponse;
import com.remelearning.english.listening.library.service.ListeningLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
}
