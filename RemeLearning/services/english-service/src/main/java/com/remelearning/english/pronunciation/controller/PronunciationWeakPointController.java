package com.remelearning.english.pronunciation.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.pronunciation.domain.PronunciationType;
import com.remelearning.english.pronunciation.domain.PronunciationWeakPoint;
import com.remelearning.english.pronunciation.service.PronunciationWeakPointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Pronunciation Weak Points", description = "Recurring/forgotten pronunciation issues, classified by sound/aspect")
@RestController
@RequestMapping("/api/v1/pronunciation/weak-points")
@RequiredArgsConstructor
public class PronunciationWeakPointController {

	private final PronunciationWeakPointService pronunciationWeakPointService;

	@Operation(summary = "List a learner's recurring pronunciation issues, optionally filtered by type "
			+ "(VOWEL, CONSONANT, STRESS, INTONATION, LINKING, RHYTHM, OTHER), sorted by forgetting score desc")
	@GetMapping("/{userId}")
	public ApiResponse<List<PronunciationWeakPoint>> getByUser(
			@PathVariable String userId,
			@RequestParam(required = false) PronunciationType type) {
		return ApiResponse.ok(pronunciationWeakPointService.getWeakPoints(userId, type));
	}

	@Operation(summary = "Same as GET /{userId}, grouped by pronunciation type")
	@GetMapping("/{userId}/grouped")
	public ApiResponse<Map<PronunciationType, List<PronunciationWeakPoint>>> getByUserGrouped(
			@PathVariable String userId) {
		List<PronunciationWeakPoint> all = pronunciationWeakPointService.getWeakPoints(userId, null);
		Map<PronunciationType, List<PronunciationWeakPoint>> grouped = all.stream()
				.collect(Collectors.groupingBy(PronunciationWeakPoint::getPronunciationType));
		return ApiResponse.ok(grouped);
	}
}
