package com.remelearning.english.grammar.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.english.grammar.domain.GrammarType;
import com.remelearning.english.grammar.domain.GrammarWeakPoint;
import com.remelearning.english.grammar.service.GrammarWeakPointService;
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

@Tag(name = "Grammar Weak Points", description = "Recurring/forgotten grammar rules, classified by rule type")
@RestController
@RequestMapping("/api/v1/grammar/weak-points")
@RequiredArgsConstructor
public class GrammarWeakPointController {

	private final GrammarWeakPointService grammarWeakPointService;

	@Operation(summary = "List a learner's recurring grammar mistakes, optionally filtered by rule type "
			+ "(TENSE, SUBJECT_VERB_AGREEMENT, ARTICLE, PREPOSITION, WORD_ORDER, PLURAL, PUNCTUATION, OTHER), "
			+ "sorted by forgetting score desc")
	@GetMapping("/{userId}")
	public ApiResponse<List<GrammarWeakPoint>> getByUser(
			@PathVariable String userId,
			@RequestParam(required = false) GrammarType type) {
		return ApiResponse.ok(grammarWeakPointService.getWeakPoints(userId, type));
	}

	@Operation(summary = "Same as GET /{userId}, grouped by grammar rule type")
	@GetMapping("/{userId}/grouped")
	public ApiResponse<Map<GrammarType, List<GrammarWeakPoint>>> getByUserGrouped(@PathVariable String userId) {
		List<GrammarWeakPoint> all = grammarWeakPointService.getWeakPoints(userId, null);
		Map<GrammarType, List<GrammarWeakPoint>> grouped = all.stream()
				.collect(Collectors.groupingBy(GrammarWeakPoint::getGrammarType));
		return ApiResponse.ok(grouped);
	}
}
