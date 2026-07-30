package com.remelearning.english.writing.library.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.writing.domain.WritingTaskType;
import com.remelearning.english.writing.dto.WritingPracticeItemDto;
import com.remelearning.english.writing.library.dto.SubmitWritingLibraryAnswerRequest;
import com.remelearning.english.writing.library.dto.SubmitWritingLibraryAnswerResponse;
import com.remelearning.english.writing.library.dto.WritingLibraryPromptDto;
import com.remelearning.english.writing.library.dto.WritingLibraryTopicDto;
import com.remelearning.english.writing.library.service.WritingLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Writing Library",
		description = "Fixed catalogue of writing/translation prompts, browsable along three independent taxonomy axes "
				+ "(grammar rules, text genres, vocabulary themes) with per-axis progress gating")
@RestController
@RequestMapping("/api/v1/learn/writing/library")
@RequiredArgsConstructor
public class WritingLibraryController {

	private final WritingLibraryService writingLibraryService;

	@Operation(summary = "Topics on one taxonomy axis (grammar | genre | vocab_theme) with this learner's gating status "
			+ "and how far through each topic's prompt chain they are; opens the axis's first topic for a new learner")
	@GetMapping("/{userId}/topics")
	public ApiResponse<List<WritingLibraryTopicDto>> getTopics(
			@PathVariable String userId, @RequestParam String taxonomy) {
		return ApiResponse.ok(writingLibraryService.getTopics(userId, taxonomy));
	}

	@Operation(summary = "The next prompt the learner still owes in this topic's chain, generating one via AI if the "
			+ "chain is not yet at full length; resumes an already-started prompt otherwise")
	@PostMapping("/{userId}/topics/{topicId}/prompts")
	public ApiResponse<WritingLibraryPromptDto> startOrResumePrompt(
			@PathVariable String userId, @PathVariable Long topicId, @RequestParam WritingTaskType taskType,
			@RequestParam(required = false) String examType) {
		return ApiResponse.ok(writingLibraryService.startOrResumePrompt(userId, topicId, taskType, examType));
	}

	@Operation(summary = "Grade a submitted library text, reveal the reference answer, feed its labelled errors into the "
			+ "learner's grammar/vocabulary weak points, and advance the topic chain (unlocking the next topic on the "
			+ "same axis once the whole chain is passed)")
	@PostMapping("/{userId}/prompts/{promptId}/submit")
	public ApiResponse<SubmitWritingLibraryAnswerResponse> submitAnswer(
			@PathVariable String userId, @PathVariable Long promptId,
			@Valid @RequestBody SubmitWritingLibraryAnswerRequest request) {
		return ApiResponse.ok(writingLibraryService.submitAnswer(userId, promptId, request));
	}

	@Operation(summary = "Generate \"học thường\" AI practice targeted at one library attempt's own mistakes; lands in the "
			+ "same practice bank as the learn tab")
	@PostMapping("/{userId}/attempts/{attemptId}/ai-practice")
	public ApiResponse<List<WritingPracticeItemDto>> generatePracticeFromAttempt(
			@PathVariable String userId, @PathVariable Long attemptId,
			@RequestParam(required = false) String examType) {
		return ApiResponse.ok(writingLibraryService.generatePracticeFromAttempt(userId, attemptId, examType));
	}
}
