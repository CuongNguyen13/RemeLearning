package com.remelearning.english.vocabulary.library.session;

import com.remelearning.english.vocabulary.library.domain.SectionQueueEntry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SectionQueueTest {

	@Test
	void initialContainsEveryWordExactlyOnceWithZeroStreakAndNoIntro() {
		List<SectionQueueEntry> queue = SectionQueue.initial(List.of(1L, 2L, 3L));

		assertThat(queue).hasSize(3);
		assertThat(queue).extracting(SectionQueueEntry::getLibraryWordId).containsExactlyInAnyOrder(1L, 2L, 3L);
		assertThat(queue).allMatch(entry -> entry.getStreak() == 0 && !entry.isIntroShown());
	}

	@Test
	void isCompleteTrueOnlyForEmptyQueue() {
		assertThat(SectionQueue.isComplete(List.of())).isTrue();
		assertThat(SectionQueue.isComplete(SectionQueue.initial(List.of(1L)))).isFalse();
	}

	@Test
	void currentReturnsFrontEntryAndThrowsWhenEmpty() {
		List<SectionQueueEntry> queue = SectionQueue.initial(List.of(1L));

		assertThat(SectionQueue.current(queue).getLibraryWordId()).isEqualTo(1L);
		assertThatThrownBy(() -> SectionQueue.current(List.of())).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void acknowledgeIntroMarksFrontEntryShownWithoutMovingItOrChangingStreak() {
		List<SectionQueueEntry> queue = new ArrayList<>(List.of(
				SectionQueueEntry.builder().libraryWordId(1L).streak(0).introShown(false).build(),
				SectionQueueEntry.builder().libraryWordId(2L).streak(0).introShown(false).build()));

		List<SectionQueueEntry> updated = SectionQueue.acknowledgeIntro(queue);

		assertThat(updated).hasSize(2);
		assertThat(updated.get(0).getLibraryWordId()).isEqualTo(1L);
		assertThat(updated.get(0).isIntroShown()).isTrue();
		assertThat(updated.get(0).getStreak()).isZero();
		assertThat(updated.get(1).getLibraryWordId()).isEqualTo(2L);
	}

	@Test
	void applyResultRemovesWordOnceMasteryStreakReached() {
		List<SectionQueueEntry> queue = new ArrayList<>(List.of(
				SectionQueueEntry.builder().libraryWordId(1L).streak(1).introShown(true).build(),
				SectionQueueEntry.builder().libraryWordId(2L).streak(0).introShown(true).build()));

		List<SectionQueueEntry> updated = SectionQueue.applyResult(queue, true);

		assertThat(updated).extracting(SectionQueueEntry::getLibraryWordId).containsExactly(2L);
	}

	@Test
	void applyResultRequeuesCorrectAnswerFurtherAwayThanWrongAnswer() {
		List<SectionQueueEntry> tenEntries = new ArrayList<>();
		tenEntries.add(SectionQueueEntry.builder().libraryWordId(0L).streak(0).introShown(true).build());
		for (long i = 1; i <= 9; i++) {
			tenEntries.add(SectionQueueEntry.builder().libraryWordId(i).streak(0).introShown(true).build());
		}

		List<SectionQueueEntry> afterCorrect = SectionQueue.applyResult(new ArrayList<>(tenEntries), true);
		List<SectionQueueEntry> afterWrong = SectionQueue.applyResult(new ArrayList<>(tenEntries), false);

		assertThat(afterCorrect.indexOf(afterCorrect.stream().filter(e -> e.getLibraryWordId() == 0L).findFirst().orElseThrow()))
				.isEqualTo(SectionQueue.CORRECT_REQUEUE_GAP);
		assertThat(afterWrong.indexOf(afterWrong.stream().filter(e -> e.getLibraryWordId() == 0L).findFirst().orElseThrow()))
				.isEqualTo(SectionQueue.WRONG_REQUEUE_GAP);
	}

	@Test
	void applyResultResetsStreakToZeroOnWrongAnswerAndRestartsIntroShownTrue() {
		List<SectionQueueEntry> queue = new ArrayList<>(List.of(
				SectionQueueEntry.builder().libraryWordId(1L).streak(1).introShown(true).build()));

		List<SectionQueueEntry> updated = SectionQueue.applyResult(queue, false);

		assertThat(updated).hasSize(1);
		assertThat(updated.get(0).getStreak()).isZero();
		assertThat(updated.get(0).isIntroShown()).isTrue();
	}

	@Test
	void applyResultClearsPendingExerciseTypeSoNextOccurrenceRerolls() {
		List<SectionQueueEntry> queue = new ArrayList<>(List.of(
				SectionQueueEntry.builder().libraryWordId(1L).streak(0).introShown(true)
						.pendingExerciseType(com.remelearning.english.vocabulary.library.domain.SectionExerciseType.MCQ).build(),
				SectionQueueEntry.builder().libraryWordId(2L).streak(0).introShown(true).build()));

		List<SectionQueueEntry> updated = SectionQueue.applyResult(queue, false);

		assertThat(updated).extracting(SectionQueueEntry::getPendingExerciseType).containsOnlyNulls();
	}
}
