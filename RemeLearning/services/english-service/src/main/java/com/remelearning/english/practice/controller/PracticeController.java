package com.remelearning.english.practice.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.practice.dto.PracticeRedoRequest;
import com.remelearning.english.practice.service.PracticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Practice", description = "Grading redo-exercise attempts, refreshing mistake history and re-triggering forgetting-score analysis")
@RestController
@RequestMapping("/api/v1/practice")
@RequiredArgsConstructor
public class PracticeController {

	private final PracticeService practiceService;

	@Operation(summary = "Submit the graded results of a learner redoing an exercise (vocabulary/grammar/pronunciation "
			+ "items mixed together); grades and logs each attempt, refreshes mistake history, and asks ai-service "
			+ "to recompute forgetting scores so recommendations are re-proposed")
	@PostMapping("/redo")
	public ApiResponse<Void> redo(@Valid @RequestBody PracticeRedoRequest request) {
		practiceService.redo(request);
		return ApiResponse.ok(null);
	}
}
