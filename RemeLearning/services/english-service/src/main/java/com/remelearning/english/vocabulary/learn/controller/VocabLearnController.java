package com.remelearning.english.vocabulary.learn.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.vocabulary.learn.dto.GenerateVocabPracticeRequest;
import com.remelearning.english.vocabulary.learn.dto.SubmitVocabAttemptRequest;
import com.remelearning.english.vocabulary.learn.dto.VocabAttemptDetailDto;
import com.remelearning.english.vocabulary.learn.dto.VocabAttemptHistoryEntryDto;
import com.remelearning.english.vocabulary.learn.dto.VocabAttemptResultDto;
import com.remelearning.english.vocabulary.learn.dto.VocabPracticeItemDto;
import com.remelearning.english.vocabulary.learn.service.VocabLearnService;
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

@Tag(name = "Vocabulary Learn", description = "AI-generated vocabulary practice sets (cloze/MCQ/matching-by-meaning) and graded attempts")
@RestController
@RequestMapping("/api/v1/learn/vocabulary")
@RequiredArgsConstructor
public class VocabLearnController {

	private final VocabLearnService vocabLearnService;

	@Operation(summary = "Generate one AI vocabulary practice set, targeting the given focus words or (if omitted) the learner's own top weak points")
	@PostMapping("/{userId}/generate")
	public ApiResponse<VocabPracticeItemDto> generate(
			@PathVariable String userId, @RequestBody(required = false) GenerateVocabPracticeRequest request) {
		return ApiResponse.ok(vocabLearnService.generate(userId, request == null ? new GenerateVocabPracticeRequest() : request));
	}

	@Operation(summary = "Full detail (questions, no answers) for one practice set")
	@GetMapping("/items/{itemId}")
	public ApiResponse<VocabPracticeItemDto> getItem(@PathVariable Long itemId) {
		return ApiResponse.ok(vocabLearnService.getItem(itemId));
	}

	@Operation(summary = "A learner's generated practice sets, newest first")
	@GetMapping("/{userId}/items")
	public ApiResponse<List<VocabPracticeItemDto>> listItems(@PathVariable String userId) {
		return ApiResponse.ok(vocabLearnService.listItems(userId));
	}

	@Operation(summary = "Grade a submitted attempt; feeds each graded word into the existing weak-point/spaced-repetition pipeline")
	@PostMapping("/attempts")
	public ApiResponse<VocabAttemptResultDto> submit(@Valid @RequestBody SubmitVocabAttemptRequest request) {
		return ApiResponse.ok(vocabLearnService.submit(request));
	}

	@Operation(summary = "A learner's past vocabulary-practice attempts, newest first")
	@GetMapping("/history/{userId}")
	public ApiResponse<List<VocabAttemptHistoryEntryDto>> getHistory(@PathVariable String userId) {
		return ApiResponse.ok(vocabLearnService.getHistory(userId));
	}

	@Operation(summary = "Full detail for one of a learner's own past attempts")
	@GetMapping("/history/{userId}/{attemptId}")
	public ApiResponse<VocabAttemptDetailDto> getAttemptDetail(@PathVariable String userId, @PathVariable Long attemptId) {
		return ApiResponse.ok(vocabLearnService.getAttemptDetail(userId, attemptId));
	}
}
