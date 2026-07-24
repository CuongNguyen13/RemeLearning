package com.remelearning.english.speaking.history.service;

import com.remelearning.english.speaking.dto.SpeakingAttemptHistoryEntryDto;
import com.remelearning.english.speaking.history.dto.SpeakingHistoryEntryDto;
import com.remelearning.english.speaking.library.domain.SpeakingLibraryAttempt;
import com.remelearning.english.speaking.library.service.SpeakingLibraryService;
import com.remelearning.english.speaking.service.SpeakingLearnService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpeakingHistoryServiceImplTest {

	private final SpeakingLearnService speakingLearnService = mock(SpeakingLearnService.class);
	private final SpeakingLibraryService speakingLibraryService = mock(SpeakingLibraryService.class);
	private final SpeakingHistoryServiceImpl service = new SpeakingHistoryServiceImpl(speakingLearnService, speakingLibraryService);

	@Test
	void getMergedHistorySortsLearnAndLibraryEntriesDescendingByCompletedAt() {
		Instant older = Instant.parse("2026-07-20T00:00:00Z");
		Instant newer = Instant.parse("2026-07-23T00:00:00Z");
		Instant newest = Instant.parse("2026-07-24T00:00:00Z");

		when(speakingLearnService.getHistory("user-1")).thenReturn(List.of(
				SpeakingAttemptHistoryEntryDto.builder().attemptId(10L).overallScore(0.5).attemptedAt(older).build(),
				SpeakingAttemptHistoryEntryDto.builder().attemptId(11L).overallScore(0.9).attemptedAt(newest).build()));
		when(speakingLibraryService.getHistory("user-1")).thenReturn(List.of(
				SpeakingLibraryAttempt.builder().id(20L).sectionId(3L).phonemeScore(1.0).wordScore(1.0).createdAt(newer).build()));

		List<SpeakingHistoryEntryDto> merged = service.getMergedHistory("user-1");

		assertThat(merged).hasSize(3);
		assertThat(merged.get(0).getAttemptOrSessionId()).isEqualTo(11L);
		assertThat(merged.get(0).getSource()).isEqualTo("LEARN");
		assertThat(merged.get(0).getSectionId()).isNull();
		assertThat(merged.get(1).getAttemptOrSessionId()).isEqualTo(20L);
		assertThat(merged.get(1).getSource()).isEqualTo("LIBRARY");
		assertThat(merged.get(1).getSectionId()).isEqualTo(3L);
		assertThat(merged.get(1).getScore()).isEqualTo(1.0);
		assertThat(merged.get(2).getAttemptOrSessionId()).isEqualTo(10L);
	}

	@Test
	void getMergedHistoryAveragesPhonemeAndWordScoreForLibraryEntries() {
		Instant completedAt = Instant.parse("2026-07-24T00:00:00Z");
		when(speakingLearnService.getHistory("user-1")).thenReturn(List.of());
		when(speakingLibraryService.getHistory("user-1")).thenReturn(List.of(
				SpeakingLibraryAttempt.builder().id(30L).sectionId(4L).phonemeScore(0.8).wordScore(0.6).createdAt(completedAt).build()));

		List<SpeakingHistoryEntryDto> merged = service.getMergedHistory("user-1");

		assertThat(merged).hasSize(1);
		assertThat(merged.get(0).getScore()).isEqualTo(0.7);
	}

	@Test
	void getMergedHistoryFallsBackToNonNullSideWhenOneLibraryScoreIsMissing() {
		Instant completedAt = Instant.parse("2026-07-24T00:00:00Z");
		when(speakingLearnService.getHistory("user-1")).thenReturn(List.of());
		when(speakingLibraryService.getHistory("user-1")).thenReturn(List.of(
				SpeakingLibraryAttempt.builder().id(31L).sectionId(4L).phonemeScore(0.8).wordScore(null).createdAt(completedAt).build()));

		List<SpeakingHistoryEntryDto> merged = service.getMergedHistory("user-1");

		assertThat(merged.get(0).getScore()).isEqualTo(0.8);
	}

	@Test
	void getMergedHistoryReturnsEmptyWhenNeitherSourceHasEntries() {
		when(speakingLearnService.getHistory("user-1")).thenReturn(List.of());
		when(speakingLibraryService.getHistory("user-1")).thenReturn(List.of());

		assertThat(service.getMergedHistory("user-1")).isEmpty();
	}
}
