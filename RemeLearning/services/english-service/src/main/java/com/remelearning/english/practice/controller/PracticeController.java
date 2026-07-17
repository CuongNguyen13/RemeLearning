package com.remelearning.english.practice.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.dto.ReviewQueueItem;
import com.remelearning.english.practice.service.PracticeService;
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

@Tag(name = "Practice", description = "Grading redo-exercise attempts, refreshing mistake history and re-triggering forgetting-score analysis")
@RestController
@RequestMapping("/api/v1/practice")
@RequiredArgsConstructor
public class PracticeController {

	private final PracticeService practiceService;

	@Operation(summary = "Submit the graded results of a learner redoing an exercise (vocabulary/grammar/pronunciation "
			+ "items mixed together); grades and logs each attempt, scores each one directly against the Java scoring "
			+ "engine (updating the owning domain's weak-point row immediately), and also asks ai-service to "
			+ "recompute forgetting scores so recommendation-service/dashboard-service stay in sync")
	@PostMapping("/redo")
	public ApiResponse<Void> redo(@Valid @RequestBody PracticeRedoRequest request) {
		practiceService.redo(request);
		return ApiResponse.ok(null);
	}

	@Operation(summary = "Items due for review now or earlier, per the Leitner spaced-repetition schedule the Java "
			+ "scoring engine maintains, soonest-first")
	@GetMapping("/review-queue/{userId}")
	public ApiResponse<List<ReviewQueueItem>> getReviewQueue(@PathVariable String userId) {
		return ApiResponse.ok(practiceService.getReviewQueue(userId));
	}
}
