package com.remelearning.english.grammar.learn.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.grammar.learn.dto.GenerateGrammarPracticeRequest;
import com.remelearning.english.grammar.learn.dto.SubmitGrammarAttemptRequest;
import com.remelearning.english.grammar.learn.dto.GrammarAttemptDetailDto;
import com.remelearning.english.grammar.learn.dto.GrammarAttemptHistoryEntryDto;
import com.remelearning.english.grammar.learn.dto.GrammarAttemptResultDto;
import com.remelearning.english.grammar.learn.dto.GrammarPracticeItemDto;
import com.remelearning.english.grammar.learn.service.GrammarLearnService;
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

@Tag(name = "Grammar Learn", description = "AI-generated grammar practice sets (error-correction/fill-tense/transform/MCQ) and graded attempts")
@RestController
@RequestMapping("/api/v1/learn/grammar")
@RequiredArgsConstructor
public class GrammarLearnController {

	private final GrammarLearnService grammarLearnService;

	@Operation(summary = "Generate one AI grammar practice set, targeting the given focus rules or (if omitted) the learner's own top weak points")
	@PostMapping("/{userId}/generate")
	public ApiResponse<GrammarPracticeItemDto> generate(
			@PathVariable String userId, @RequestBody(required = false) GenerateGrammarPracticeRequest request) {
		return ApiResponse.ok(grammarLearnService.generate(userId, request == null ? new GenerateGrammarPracticeRequest() : request));
	}

	@Operation(summary = "Full detail (questions, no answers) for one practice set")
	@GetMapping("/items/{itemId}")
	public ApiResponse<GrammarPracticeItemDto> getItem(@PathVariable Long itemId) {
		return ApiResponse.ok(grammarLearnService.getItem(itemId));
	}

	@Operation(summary = "A learner's generated practice sets, newest first")
	@GetMapping("/{userId}/items")
	public ApiResponse<List<GrammarPracticeItemDto>> listItems(@PathVariable String userId) {
		return ApiResponse.ok(grammarLearnService.listItems(userId));
	}

	@Operation(summary = "Grade a submitted attempt; feeds each graded rule into the existing weak-point/spaced-repetition pipeline")
	@PostMapping("/attempts")
	public ApiResponse<GrammarAttemptResultDto> submit(@Valid @RequestBody SubmitGrammarAttemptRequest request) {
		return ApiResponse.ok(grammarLearnService.submit(request));
	}

	@Operation(summary = "A learner's past grammar-practice attempts, newest first")
	@GetMapping("/history/{userId}")
	public ApiResponse<List<GrammarAttemptHistoryEntryDto>> getHistory(@PathVariable String userId) {
		return ApiResponse.ok(grammarLearnService.getHistory(userId));
	}

	@Operation(summary = "Full detail for one of a learner's own past attempts")
	@GetMapping("/history/{userId}/{attemptId}")
	public ApiResponse<GrammarAttemptDetailDto> getAttemptDetail(@PathVariable String userId, @PathVariable Long attemptId) {
		return ApiResponse.ok(grammarLearnService.getAttemptDetail(userId, attemptId));
	}

	@Operation(summary = "Generate AI practice targeted at one specific past attempt's mistakes (the \"Luyện tập với AI\" action from a history row)")
	@PostMapping("/history/{userId}/{attemptId}/ai-practice")
	public ApiResponse<List<GrammarPracticeItemDto>> generateFromAttempt(
			@PathVariable String userId, @PathVariable Long attemptId) {
		return ApiResponse.ok(grammarLearnService.generatePracticeFromAttempt(userId, attemptId));
	}
}
