package com.remelearning.english.grammar.library.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.grammar.library.dto.FinishGrammarLibrarySessionResponse;
import com.remelearning.english.grammar.library.dto.GrammarLibraryAnswerResultDto;
import com.remelearning.english.grammar.library.dto.GrammarLibraryContentDto;
import com.remelearning.english.grammar.library.dto.GrammarLibraryHistoryEntryDto;
import com.remelearning.english.grammar.library.dto.GrammarLibraryTopicDto;
import com.remelearning.english.grammar.library.dto.StartGrammarSessionResponse;
import com.remelearning.english.grammar.library.dto.SubmitGrammarLibraryAnswerRequest;
import com.remelearning.english.grammar.learn.dto.GrammarPracticeItemDto;
import com.remelearning.english.grammar.library.service.GrammarLibraryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Grammar Library", description = "60-topic grammar theory + practice library with pass/retry/unlock-next-topic progression")
@RestController
@RequestMapping("/api/v1/learn/grammar/library")
@RequiredArgsConstructor
public class GrammarLibraryController {

	private final GrammarLibraryService grammarLibraryService;

	@Operation(summary = "List every one of the 60 catalog topics with this learner's own progression status")
	@GetMapping("/{userId}/topics")
	public ApiResponse<List<GrammarLibraryTopicDto>> listTopics(@PathVariable String userId) {
		return ApiResponse.ok(grammarLibraryService.listTopics(userId));
	}

	@Operation(summary = "A topic's theory page + question pool, generating it via AI on first read only")
	@GetMapping("/topics/{topicId}")
	public ApiResponse<GrammarLibraryContentDto> getTopicContent(@PathVariable Long topicId) {
		return ApiResponse.ok(grammarLibraryService.getTopicContent(topicId));
	}

	@Operation(summary = "Start a new INITIAL session for a topic (must be UNLOCKED or IN_PROGRESS)")
	@PostMapping("/{userId}/topics/{topicId}/sessions")
	public ApiResponse<StartGrammarSessionResponse> startSession(@PathVariable String userId, @PathVariable Long topicId) {
		return ApiResponse.ok(grammarLibraryService.startSession(userId, topicId));
	}

	@Operation(summary = "Grade one submitted answer within an in-progress session")
	@PostMapping("/sessions/{sessionId}/answers")
	public ApiResponse<GrammarLibraryAnswerResultDto> submitAnswer(
			@PathVariable Long sessionId, @Valid @RequestBody SubmitGrammarLibraryAnswerRequest request) {
		return ApiResponse.ok(grammarLibraryService.submitAnswer(sessionId, request));
	}

	@Operation(summary = "Finish a session: PASSED + next-topic unlock when all correct, otherwise a new RETRY session for the missed questions")
	@PostMapping("/sessions/{sessionId}/finish")
	public ApiResponse<FinishGrammarLibrarySessionResponse> finishSession(@PathVariable Long sessionId) {
		return ApiResponse.ok(grammarLibraryService.finishSession(sessionId));
	}

	@Operation(summary = "A learner's completed sessions for one topic, newest first")
	@GetMapping("/{userId}/topics/{topicId}/history")
	public ApiResponse<List<GrammarLibraryHistoryEntryDto>> getHistory(@PathVariable String userId, @PathVariable Long topicId) {
		return ApiResponse.ok(grammarLibraryService.getHistory(userId, topicId));
	}

	@Operation(summary = "Generate AI practice targeted at one past session's missed questions (the \"Luyện tập với AI\" action) - persists into the same grammar_practice_items bank the learn flow uses")
	@PostMapping("/{userId}/sessions/{sessionId}/ai-practice")
	public ApiResponse<List<GrammarPracticeItemDto>> generateFromSession(
			@PathVariable String userId, @PathVariable Long sessionId) {
		return ApiResponse.ok(grammarLibraryService.generatePracticeFromSession(userId, sessionId));
	}
}
