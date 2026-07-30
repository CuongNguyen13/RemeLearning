package com.remelearning.english.writing.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.writing.domain.WritingSuggestion;
import com.remelearning.english.writing.dto.GenerateWritingPracticeRequest;
import com.remelearning.english.writing.dto.SubmitWritingAttemptRequest;
import com.remelearning.english.writing.dto.SuggestNextSentenceRequest;
import com.remelearning.english.writing.dto.WritingAttemptDetailDto;
import com.remelearning.english.writing.dto.WritingAttemptHistoryEntryDto;
import com.remelearning.english.writing.dto.WritingAttemptResultDto;
import com.remelearning.english.writing.dto.WritingPracticeItemDto;
import com.remelearning.english.writing.service.WritingLearnService;
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

@Tag(name = "Writing Learn",
		description = "AI-generated writing briefs and translation passages (VI↔EN), graded per criterion with labelled "
				+ "grammar/vocabulary errors that feed the existing weak-point pipeline")
@RestController
@RequestMapping("/api/v1/learn/writing")
@RequiredArgsConstructor
public class WritingLearnController {

	private final WritingLearnService writingLearnService;

	@Operation(summary = "Generate one AI writing brief or translation passage for the requested task type, targeting the "
			+ "given focus items or (if omitted) the learner's own most-forgotten grammar and vocabulary labels")
	@PostMapping("/{userId}/generate")
	public ApiResponse<WritingPracticeItemDto> generate(
			@PathVariable String userId, @Valid @RequestBody GenerateWritingPracticeRequest request) {
		return ApiResponse.ok(writingLearnService.generate(userId, request));
	}

	@Operation(summary = "One practice prompt, without its reference answer")
	@GetMapping("/items/{itemId}")
	public ApiResponse<WritingPracticeItemDto> getItem(@PathVariable Long itemId) {
		return ApiResponse.ok(writingLearnService.getItem(itemId));
	}

	@Operation(summary = "A learner's generated practice prompts, newest first")
	@GetMapping("/{userId}/items")
	public ApiResponse<List<WritingPracticeItemDto>> listItems(@PathVariable String userId) {
		return ApiResponse.ok(writingLearnService.listItems(userId));
	}

	@Operation(summary = "2-3 hints for the learner's next sentence (Vietnamese idea + English structure/phrases, never a "
			+ "ready-made sentence; for translation tasks the reference answer is never used)")
	@PostMapping("/suggest")
	public ApiResponse<List<WritingSuggestion>> suggest(@Valid @RequestBody SuggestNextSentenceRequest request) {
		return ApiResponse.ok(writingLearnService.suggest(request));
	}

	@Operation(summary = "Grade a submitted text per criterion (grammar/vocabulary/coherence + accuracy or task response), "
			+ "reveal the reference answer, and feed every labelled error into the learner's grammar/vocabulary weak "
			+ "points, review queue and recommendations")
	@PostMapping("/attempts")
	public ApiResponse<WritingAttemptResultDto> submit(@Valid @RequestBody SubmitWritingAttemptRequest request) {
		return ApiResponse.ok(writingLearnService.submit(request));
	}

	@Operation(summary = "A learner's past writing-practice attempts, newest first")
	@GetMapping("/history/{userId}")
	public ApiResponse<List<WritingAttemptHistoryEntryDto>> getHistory(@PathVariable String userId) {
		return ApiResponse.ok(writingLearnService.getHistory(userId));
	}

	@Operation(summary = "Full detail for one of a learner's own past attempts")
	@GetMapping("/history/{userId}/{attemptId}")
	public ApiResponse<WritingAttemptDetailDto> getAttemptDetail(
			@PathVariable String userId, @PathVariable Long attemptId) {
		return ApiResponse.ok(writingLearnService.getAttemptDetail(userId, attemptId));
	}

	@Operation(summary = "Generate practice targeted at one specific past attempt's mistakes (the \"Luyện lại những lỗi "
			+ "này\" action from a result panel or history row)")
	@PostMapping("/history/{userId}/{attemptId}/ai-practice")
	public ApiResponse<List<WritingPracticeItemDto>> generateFromAttempt(
			@PathVariable String userId, @PathVariable Long attemptId) {
		return ApiResponse.ok(writingLearnService.generatePracticeFromAttempt(userId, attemptId));
	}
}
