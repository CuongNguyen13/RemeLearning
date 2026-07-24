package com.remelearning.english.listening.history.service;

import com.remelearning.english.listening.dto.ListeningAttemptHistoryEntryDto;
import com.remelearning.english.listening.history.dto.ListeningHistoryEntryDto;
import com.remelearning.english.listening.library.domain.ListeningLibraryAttempt;
import com.remelearning.english.listening.library.service.ListeningLibraryService;
import com.remelearning.english.listening.service.ListeningLearnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Orchestrates the merged listening history endpoint. Deliberately a standalone service (not
 * folded into {@code ListeningLearnServiceImpl} or {@code ListeningLibraryServiceImpl}) because
 * {@code ListeningLibraryServiceImpl} already depends on {@code ListeningLearnService} (for
 * generatePracticeFromSection's delegation) — having {@code ListeningLearnServiceImpl} depend back
 * on {@code ListeningLibraryService} would form a circular bean dependency. This class depends on
 * both interfaces one level up, so neither existing service needs to know about the other. Mirrors
 * {@code GrammarHistoryServiceImpl}'s resolution of the same design question.
 */
@Service
@RequiredArgsConstructor
public class ListeningHistoryServiceImpl implements ListeningHistoryService {

	private final ListeningLearnService listeningLearnService;
	private final ListeningLibraryService listeningLibraryService;

	// Normalizes both history shapes into ListeningHistoryEntryDto, then merges and sorts
	// descending by completion time so the FE renders one unified, time-ordered list.
	@Override
	public List<ListeningHistoryEntryDto> getMergedHistory(String userId) {
		List<ListeningHistoryEntryDto> learnEntries = listeningLearnService.getHistory(userId).stream()
				.map(this::fromLearn)
				.toList();
		List<ListeningHistoryEntryDto> libraryEntries = listeningLibraryService.getHistory(userId).stream()
				.map(this::fromLibrary)
				.toList();
		return Stream.concat(learnEntries.stream(), libraryEntries.stream())
				.sorted(Comparator.comparing(ListeningHistoryEntryDto::getCompletedAt).reversed())
				.toList();
	}

	private ListeningHistoryEntryDto fromLearn(ListeningAttemptHistoryEntryDto row) {
		return ListeningHistoryEntryDto.builder()
				.source("LEARN")
				.attemptOrSessionId(row.getAttemptId())
				.completedAt(row.getAttemptedAt())
				.score(row.getScore())
				.sectionId(null)
				.topicId(null)
				.build();
	}

	private ListeningHistoryEntryDto fromLibrary(ListeningLibraryAttempt row) {
		return ListeningHistoryEntryDto.builder()
				.source("LIBRARY")
				.attemptOrSessionId(row.getId())
				.completedAt(row.getCompletedAt())
				.score(row.getScore())
				.sectionId(row.getSectionId())
				.topicId(listeningLibraryService.resolveTopicId(row.getSectionId()))
				.build();
	}
}
