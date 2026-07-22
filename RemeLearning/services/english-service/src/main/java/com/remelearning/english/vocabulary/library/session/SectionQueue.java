package com.remelearning.english.vocabulary.library.session;

import com.remelearning.english.vocabulary.library.domain.SectionQueueEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pure, stateless Leitner-lite scheduler for one Section's in-session word queue. A word is
 * dropped from the queue (considered "mastered for this session") once it has been answered
 * correctly {@link #MASTERY_STREAK} times in a row; any wrong answer resets its streak to zero.
 * Correct-but-not-yet-mastered answers are requeued further away than wrong answers, giving an
 * expanding-interval spacing effect within the single session (the same idea as a physical Leitner
 * box, compressed into one sitting instead of days).
 */
public final class SectionQueue {

	/** Consecutive correct answers needed before a word leaves the queue for this session. */
	public static final int MASTERY_STREAK = 2;
	/** How many cards later a correct-but-not-mastered word reappears (capped by queue length). */
	public static final int CORRECT_REQUEUE_GAP = 6;
	/** How many cards later a wrongly-answered word reappears (capped by queue length). */
	public static final int WRONG_REQUEUE_GAP = 2;

	private SectionQueue() {
	}

	/** Shuffles the given word ids into a fresh queue, each starting at streak 0, intro not yet shown. */
	public static List<SectionQueueEntry> initial(List<Long> libraryWordIds) {
		List<Long> shuffled = new ArrayList<>(libraryWordIds);
		Collections.shuffle(shuffled);
		return shuffled.stream()
				.map(id -> SectionQueueEntry.builder().libraryWordId(id).streak(0).introShown(false).build())
				.collect(Collectors.toCollection(ArrayList::new));
	}

	/** A Section is done once every word has reached {@link #MASTERY_STREAK} and left the queue. */
	public static boolean isComplete(List<SectionQueueEntry> queue) {
		return queue.isEmpty();
	}

	/** The entry currently at the front of the queue - the one to present next. */
	public static SectionQueueEntry current(List<SectionQueueEntry> queue) {
		if (queue.isEmpty()) {
			throw new IllegalStateException("Cannot read the current entry of an empty section queue");
		}
		return queue.get(0);
	}

	/** Marks the front entry's INTRO flashcard as shown, without moving it or touching its streak. */
	public static List<SectionQueueEntry> acknowledgeIntro(List<SectionQueueEntry> queue) {
		List<SectionQueueEntry> updated = new ArrayList<>(queue);
		SectionQueueEntry front = updated.get(0);
		updated.set(0, SectionQueueEntry.builder()
				.libraryWordId(front.getLibraryWordId()).streak(front.getStreak()).introShown(true).build());
		return updated;
	}

	/**
	 * Pops the front entry and, based on whether its QUIZ answer was correct, either drops it
	 * (mastered) or reinserts it further into the queue - the pending exercise type is always
	 * cleared on reinsertion so the word's next occurrence rolls a fresh (possibly different) type.
	 */
	public static List<SectionQueueEntry> applyResult(List<SectionQueueEntry> queue, boolean correct) {
		SectionQueueEntry front = queue.get(0);
		List<SectionQueueEntry> remaining = new ArrayList<>(queue.subList(1, queue.size()));

		if (correct) {
			int newStreak = front.getStreak() + 1;
			if (newStreak >= MASTERY_STREAK) {
				return remaining;
			}
			int position = Math.min(remaining.size(), CORRECT_REQUEUE_GAP);
			remaining.add(position, SectionQueueEntry.builder()
					.libraryWordId(front.getLibraryWordId()).streak(newStreak).introShown(true).build());
			return remaining;
		}

		int position = Math.min(remaining.size(), WRONG_REQUEUE_GAP);
		remaining.add(position, SectionQueueEntry.builder()
				.libraryWordId(front.getLibraryWordId()).streak(0).introShown(true).build());
		return remaining;
	}
}
