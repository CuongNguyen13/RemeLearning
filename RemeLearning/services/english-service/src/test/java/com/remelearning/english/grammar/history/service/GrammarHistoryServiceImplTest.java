package com.remelearning.english.grammar.history.service;

import com.remelearning.english.grammar.history.dto.GrammarHistoryEntryDto;
import com.remelearning.english.grammar.learn.dto.GrammarAttemptHistoryEntryDto;
import com.remelearning.english.grammar.learn.service.GrammarLearnService;
import com.remelearning.english.grammar.library.dto.GrammarLibraryHistoryEntryDto;
import com.remelearning.english.grammar.library.service.GrammarLibraryService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GrammarHistoryServiceImplTest {

	private final GrammarLearnService grammarLearnService = mock(GrammarLearnService.class);
	private final GrammarLibraryService grammarLibraryService = mock(GrammarLibraryService.class);
	private final GrammarHistoryServiceImpl service = new GrammarHistoryServiceImpl(grammarLearnService, grammarLibraryService);

	@Test
	void getMergedHistorySortsLearnAndLibraryEntriesDescendingByCompletedAt() {
		Instant older = Instant.parse("2026-07-20T00:00:00Z");
		Instant newer = Instant.parse("2026-07-23T00:00:00Z");
		Instant newest = Instant.parse("2026-07-24T00:00:00Z");

		when(grammarLearnService.getHistory("user-1")).thenReturn(List.of(
				GrammarAttemptHistoryEntryDto.builder().attemptId(10L).score(0.5).attemptedAt(older).build(),
				GrammarAttemptHistoryEntryDto.builder().attemptId(11L).score(0.9).attemptedAt(newest).build()));
		when(grammarLibraryService.getHistoryForUser("user-1")).thenReturn(List.of(
				GrammarLibraryHistoryEntryDto.builder().sessionId(20L).topicId(3L).accuracy(1.0).completedAt(newer).build()));

		List<GrammarHistoryEntryDto> merged = service.getMergedHistory("user-1");

		assertThat(merged).hasSize(3);
		assertThat(merged.get(0).getAttemptOrSessionId()).isEqualTo(11L);
		assertThat(merged.get(0).getSource()).isEqualTo("LEARN");
		assertThat(merged.get(0).getTopicId()).isNull();
		assertThat(merged.get(1).getAttemptOrSessionId()).isEqualTo(20L);
		assertThat(merged.get(1).getSource()).isEqualTo("LIBRARY");
		assertThat(merged.get(1).getTopicId()).isEqualTo(3L);
		assertThat(merged.get(2).getAttemptOrSessionId()).isEqualTo(10L);
	}

	@Test
	void getMergedHistoryReturnsEmptyWhenNeitherSourceHasEntries() {
		when(grammarLearnService.getHistory("user-1")).thenReturn(List.of());
		when(grammarLibraryService.getHistoryForUser("user-1")).thenReturn(List.of());

		assertThat(service.getMergedHistory("user-1")).isEmpty();
	}
}
