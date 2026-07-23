package com.remelearning.english.speaking.library.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.speaking.library.domain.SpeakingLibraryAttempt;
import com.remelearning.english.speaking.library.dto.FinishSectionResponse;
import com.remelearning.english.speaking.library.dto.SentenceAttemptResultDto;
import com.remelearning.english.speaking.library.dto.SpeakingLibrarySectionDto;
import com.remelearning.english.speaking.library.dto.SpeakingLibraryTopicDto;
import com.remelearning.english.speaking.library.service.SpeakingLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Tag(name = "Speaking Library", description = "Fixed speaking-topic catalog with AI-generated sample-sentence Sections (text+IPA+audio), scored one sentence at a time via GOP, and pass/unlock-next-topic progression")
@RestController
@RequestMapping("/api/v1/learn/speaking/library")
@RequiredArgsConstructor
public class SpeakingLibraryController {

	private final SpeakingLibraryService speakingLibraryService;

	@Operation(summary = "List every catalog topic with this learner's own progression status (bootstraps the first topic to UNLOCKED)")
	@GetMapping("/{userId}/topics")
	public ApiResponse<List<SpeakingLibraryTopicDto>> getTopics(@PathVariable String userId) {
		return ApiResponse.ok(speakingLibraryService.getTopics(userId));
	}

	@Operation(summary = "Start a new Section for a topic, or resume its most recent one (must be UNLOCKED or IN_PROGRESS)")
	@PostMapping("/{userId}/topics/{topicId}/sections")
	public ApiResponse<SpeakingLibrarySectionDto> startOrResumeSection(
			@PathVariable String userId, @PathVariable Long topicId) {
		return ApiResponse.ok(speakingLibraryService.startOrResumeSection(userId, topicId));
	}

	@Operation(summary = "Submit a learner's recorded attempt at one sentence (multipart audio); scores it via ai-service's wav2vec2 GOP model - does not itself affect topic gating, see the finish endpoint")
	@PostMapping(value = "/{userId}/sections/{sectionId}/sentences/{sentenceId}/attempts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<SentenceAttemptResultDto> submitSentenceAttempt(
			@PathVariable String userId, @PathVariable Long sectionId, @PathVariable Long sentenceId,
			@RequestParam("audio") MultipartFile audio) {
		return ApiResponse.ok(speakingLibraryService.submitSentenceAttempt(userId, sectionId, sentenceId, audio));
	}

	@Operation(summary = "Finish a section: if every sentence has a passing attempt, marks the topic PASSED and unlocks the next one")
	@PostMapping("/{userId}/sections/{sectionId}/finish")
	public ApiResponse<FinishSectionResponse> finishSection(@PathVariable String userId, @PathVariable Long sectionId) {
		return ApiResponse.ok(speakingLibraryService.finishSection(userId, sectionId));
	}

	@Operation(summary = "This learner's scored sentence attempts, across all topics")
	@GetMapping("/{userId}/sections/history")
	public ApiResponse<List<SpeakingLibraryAttempt>> getHistory(@PathVariable String userId) {
		return ApiResponse.ok(speakingLibraryService.getHistory(userId));
	}
}
