package com.remelearning.english.grammar.library.dto;

import com.remelearning.english.grammar.library.domain.GrammarSessionType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class GrammarLibraryHistoryEntryDto {
	private Long sessionId;
	private GrammarSessionType sessionType;
	private int correctCount;
	private int totalCount;
	private double accuracy;
	private Instant completedAt;
}
