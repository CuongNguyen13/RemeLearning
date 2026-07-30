package com.remelearning.english.practice.session.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.practice.session.dto.CompletePracticeExerciseRequest;
import com.remelearning.english.practice.session.dto.PracticeSessionDto;
import com.remelearning.english.practice.session.dto.StartPracticeSessionRequest;
import com.remelearning.english.practice.session.service.PracticeSessionService;
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

@Tag(name = "Practice Session",
		description = "Orchestrates a Luyện tập session: ~4 AI exercises mixed across the four skills, aimed at top weak points")
@RestController
@RequestMapping("/api/v1/practice/sessions")
@RequiredArgsConstructor
public class PracticeSessionController {

	private final PracticeSessionService practiceSessionService;

	@Operation(summary = "Start a new practice session - generates one AI exercise per slot (mixed skills, top weak points first)")
	@PostMapping
	public ApiResponse<PracticeSessionDto> start(@Valid @RequestBody StartPracticeSessionRequest request) {
		return ApiResponse.ok(practiceSessionService.startSession(request.getUserId(), request.getExerciseCount(), request.getExamType()));
	}

	@Operation(summary = "Read back a session with its exercise slots")
	@GetMapping("/{sessionId}")
	public ApiResponse<PracticeSessionDto> get(@PathVariable Long sessionId) {
		return ApiResponse.ok(practiceSessionService.getSession(sessionId));
	}

	@Operation(summary = "The learner's most recent still-in-progress session (for resume), or null")
	@GetMapping("/latest/{userId}")
	public ApiResponse<PracticeSessionDto> getLatest(@PathVariable String userId) {
		return ApiResponse.ok(practiceSessionService.getLatestInProgress(userId));
	}

	@Operation(summary = "Record one exercise slot's score; completes the session once every slot is done")
	@PostMapping("/{sessionId}/exercises/{order}/complete")
	public ApiResponse<PracticeSessionDto> completeExercise(
			@PathVariable Long sessionId,
			@PathVariable int order,
			@RequestBody CompletePracticeExerciseRequest request) {
		return ApiResponse.ok(practiceSessionService.completeExercise(sessionId, order, request.getScore()));
	}
}
