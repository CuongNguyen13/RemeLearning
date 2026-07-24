package com.remelearning.english.grammar.history.service;

import com.remelearning.english.grammar.history.dto.GrammarHistoryEntryDto;
import com.remelearning.english.grammar.learn.dto.GrammarAttemptHistoryEntryDto;
import com.remelearning.english.grammar.learn.service.GrammarLearnService;
import com.remelearning.english.grammar.library.dto.GrammarLibraryHistoryEntryDto;
import com.remelearning.english.grammar.library.service.GrammarLibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Orchestrates the merged grammar history endpoint. Deliberately a standalone service (not folded
 * into {@code GrammarLearnServiceImpl} or {@code GrammarLibraryServiceImpl}) because
 * {@code GrammarLibraryServiceImpl} already depends on {@code GrammarLearnService} (for
 * generatePracticeFromSession's delegation) — having {@code GrammarLearnServiceImpl} depend back on
 * {@code GrammarLibraryService} would form a circular bean dependency. This class depends on both
 * interfaces one level up, so neither existing service needs to know about the other.
 */
@Service
@RequiredArgsConstructor
public class GrammarHistoryServiceImpl implements GrammarHistoryService {

	private final GrammarLearnService grammarLearnService;
	private final GrammarLibraryService grammarLibraryService;

	// Normalizes both history shapes into GrammarHistoryEntryDto, then merges and sorts descending
	// by completion time so the FE renders one unified, time-ordered list.
	@Override
	public List<GrammarHistoryEntryDto> getMergedHistory(String userId) {
		List<GrammarHistoryEntryDto> learnEntries = grammarLearnService.getHistory(userId).stream()
				.map(this::fromLearn)
				.toList();
		List<GrammarHistoryEntryDto> libraryEntries = grammarLibraryService.getHistoryForUser(userId).stream()
				.map(this::fromLibrary)
				.toList();
		return Stream.concat(learnEntries.stream(), libraryEntries.stream())
				.sorted(Comparator.comparing(GrammarHistoryEntryDto::getCompletedAt).reversed())
				.toList();
	}

	private GrammarHistoryEntryDto fromLearn(GrammarAttemptHistoryEntryDto row) {
		return GrammarHistoryEntryDto.builder()
				.source("LEARN")
				.attemptOrSessionId(row.getAttemptId())
				.completedAt(row.getAttemptedAt())
				.score(row.getScore())
				.topicId(null)
				.build();
	}

	private GrammarHistoryEntryDto fromLibrary(GrammarLibraryHistoryEntryDto row) {
		return GrammarHistoryEntryDto.builder()
				.source("LIBRARY")
				.attemptOrSessionId(row.getSessionId())
				.completedAt(row.getCompletedAt())
				.score(row.getAccuracy())
				.topicId(row.getTopicId())
				.build();
	}
}
