package com.remelearning.english.listening.history.service;

import com.remelearning.english.listening.dto.ListeningAttemptHistoryEntryDto;
import com.remelearning.english.listening.history.dto.ListeningHistoryEntryDto;
import com.remelearning.english.listening.library.domain.ListeningLibraryAttempt;
import com.remelearning.english.listening.library.service.ListeningLibraryService;
import com.remelearning.english.listening.service.ListeningLearnService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListeningHistoryServiceImplTest {

	private final ListeningLearnService listeningLearnService = mock(ListeningLearnService.class);
	private final ListeningLibraryService listeningLibraryService = mock(ListeningLibraryService.class);
	private final ListeningHistoryServiceImpl service = new ListeningHistoryServiceImpl(listeningLearnService, listeningLibraryService);

	@Test
	void getMergedHistorySortsLearnAndLibraryEntriesDescendingByCompletedAt() {
		Instant older = Instant.parse("2026-07-20T00:00:00Z");
		Instant newer = Instant.parse("2026-07-23T00:00:00Z");
		Instant newest = Instant.parse("2026-07-24T00:00:00Z");

		when(listeningLearnService.getHistory("user-1")).thenReturn(List.of(
				ListeningAttemptHistoryEntryDto.builder().attemptId(10L).score(0.5).attemptedAt(older).build(),
				ListeningAttemptHistoryEntryDto.builder().attemptId(11L).score(0.9).attemptedAt(newest).build()));
		when(listeningLibraryService.getHistory("user-1")).thenReturn(List.of(
				ListeningLibraryAttempt.builder().id(20L).sectionId(3L).score(1.0).completedAt(newer).build()));
		when(listeningLibraryService.resolveTopicId(3L)).thenReturn(7L);

		List<ListeningHistoryEntryDto> merged = service.getMergedHistory("user-1");

		assertThat(merged).hasSize(3);
		assertThat(merged.get(0).getAttemptOrSessionId()).isEqualTo(11L);
		assertThat(merged.get(0).getSource()).isEqualTo("LEARN");
		assertThat(merged.get(0).getSectionId()).isNull();
		assertThat(merged.get(0).getTopicId()).isNull();
		assertThat(merged.get(1).getAttemptOrSessionId()).isEqualTo(20L);
		assertThat(merged.get(1).getSource()).isEqualTo("LIBRARY");
		assertThat(merged.get(1).getSectionId()).isEqualTo(3L);
		assertThat(merged.get(1).getTopicId()).isEqualTo(7L);
		assertThat(merged.get(2).getAttemptOrSessionId()).isEqualTo(10L);
	}

	@Test
	void getMergedHistoryLeavesTopicIdNullWhenSectionNoLongerResolves() {
		when(listeningLearnService.getHistory("user-1")).thenReturn(List.of());
		when(listeningLibraryService.getHistory("user-1")).thenReturn(List.of(
				ListeningLibraryAttempt.builder().id(21L).sectionId(99L).score(0.5)
						.completedAt(Instant.parse("2026-07-24T00:00:00Z")).build()));
		when(listeningLibraryService.resolveTopicId(99L)).thenReturn(null);

		List<ListeningHistoryEntryDto> merged = service.getMergedHistory("user-1");

		assertThat(merged.get(0).getTopicId()).isNull();
	}

	@Test
	void getMergedHistoryReturnsEmptyWhenNeitherSourceHasEntries() {
		when(listeningLearnService.getHistory("user-1")).thenReturn(List.of());
		when(listeningLibraryService.getHistory("user-1")).thenReturn(List.of());

		assertThat(service.getMergedHistory("user-1")).isEmpty();
	}
}
