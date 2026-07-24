package com.remelearning.english.speaking.history.service;

import com.remelearning.english.speaking.dto.SpeakingAttemptHistoryEntryDto;
import com.remelearning.english.speaking.history.dto.SpeakingHistoryEntryDto;
import com.remelearning.english.speaking.library.domain.SpeakingLibraryAttempt;
import com.remelearning.english.speaking.library.service.SpeakingLibraryService;
import com.remelearning.english.speaking.service.SpeakingLearnService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Orchestrates the merged speaking history endpoint. Deliberately a standalone service (not folded
 * into {@code SpeakingLearnServiceImpl} or {@code SpeakingLibraryServiceImpl}) because
 * {@code SpeakingLibraryServiceImpl} already depends on {@code SpeakingLearnService} (for
 * generatePracticeFromSection's delegation) — having {@code SpeakingLearnServiceImpl} depend back
 * on {@code SpeakingLibraryService} would form a circular bean dependency. This class depends on
 * both interfaces one level up, so neither existing service needs to know about the other. Mirrors
 * {@code GrammarHistoryServiceImpl}/{@code ListeningHistoryServiceImpl}'s resolution of the same
 * design question. Library rows stay at {@code SpeakingLibraryService#getHistory}'s existing
 * granularity - one row per scored sentence attempt, not rolled up per section.
 */
@Service
@RequiredArgsConstructor
public class SpeakingHistoryServiceImpl implements SpeakingHistoryService {

	private final SpeakingLearnService speakingLearnService;
	private final SpeakingLibraryService speakingLibraryService;

	// Normalizes both history shapes into SpeakingHistoryEntryDto, then merges and sorts descending
	// by completion time so the FE renders one unified, time-ordered list.
	@Override
	public List<SpeakingHistoryEntryDto> getMergedHistory(String userId) {
		List<SpeakingHistoryEntryDto> learnEntries = speakingLearnService.getHistory(userId).stream()
				.map(this::fromLearn)
				.toList();
		List<SpeakingHistoryEntryDto> libraryEntries = speakingLibraryService.getHistory(userId).stream()
				.map(this::fromLibrary)
				.toList();
		return Stream.concat(learnEntries.stream(), libraryEntries.stream())
				.sorted(Comparator.comparing(SpeakingHistoryEntryDto::getCompletedAt).reversed())
				.toList();
	}

	private SpeakingHistoryEntryDto fromLearn(SpeakingAttemptHistoryEntryDto row) {
		return SpeakingHistoryEntryDto.builder()
				.source("LEARN")
				.attemptOrSessionId(row.getAttemptId())
				.completedAt(row.getAttemptedAt())
				.score(row.getOverallScore())
				.sectionId(null)
				.topicId(null)
				.build();
	}

	private SpeakingHistoryEntryDto fromLibrary(SpeakingLibraryAttempt row) {
		return SpeakingHistoryEntryDto.builder()
				.source("LIBRARY")
				.attemptOrSessionId(row.getId())
				.completedAt(row.getCreatedAt())
				.score(averageOf(row.getPhonemeScore(), row.getWordScore()))
				.sectionId(row.getSectionId())
				.topicId(speakingLibraryService.resolveTopicId(row.getSectionId()))
				.build();
	}

	// Averages phoneme and word accuracy into one pronunciation score for the merged history row -
	// both are meaningful accuracy signals, so neither alone should represent the attempt. Falls
	// back to whichever side is non-null (or null) rather than throwing, matching the previous
	// code's tolerance of a null phonemeScore.
	private Double averageOf(Double phonemeScore, Double wordScore) {
		if (phonemeScore == null) {
			return wordScore;
		}
		if (wordScore == null) {
			return phonemeScore;
		}
		return (phonemeScore + wordScore) / 2.0;
	}
}
