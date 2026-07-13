package com.remelearning.vocabulary.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.vocabulary.domain.VocabularyType;
import com.remelearning.vocabulary.domain.VocabularyWeakPoint;
import com.remelearning.vocabulary.service.VocabularyWeakPointService;
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

@Tag(name = "Vocabulary Weak Points", description = "Recurring/forgotten vocabulary items, classified by word type / phrase type")
@RestController
@RequestMapping("/api/v1/vocabulary/weak-points")
@RequiredArgsConstructor
public class VocabularyWeakPointController {

	private final VocabularyWeakPointService vocabularyWeakPointService;

	@Operation(summary = "List a learner's forgotten vocabulary items, optionally filtered by type "
			+ "(NOUN, VERB, ADJECTIVE, ADVERB, PHRASAL_VERB, COLLOCATION, IDIOM, OTHER), sorted by forgetting score desc")
	@GetMapping("/{userId}")
	public ApiResponse<List<VocabularyWeakPoint>> getByUser(
			@PathVariable String userId,
			@RequestParam(required = false) VocabularyType type) {
		return ApiResponse.ok(vocabularyWeakPointService.getWeakPoints(userId, type));
	}

	@Operation(summary = "Same as GET /{userId}, grouped by vocabulary type (word type / phrase type)")
	@GetMapping("/{userId}/grouped")
	public ApiResponse<Map<VocabularyType, List<VocabularyWeakPoint>>> getByUserGrouped(@PathVariable String userId) {
		List<VocabularyWeakPoint> all = vocabularyWeakPointService.getWeakPoints(userId, null);
		Map<VocabularyType, List<VocabularyWeakPoint>> grouped = all.stream()
				.collect(Collectors.groupingBy(VocabularyWeakPoint::getVocabularyType));
		return ApiResponse.ok(grouped);
	}
}
