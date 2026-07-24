package com.remelearning.english.listening.history.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * One row in the merged listening history list — either a "học thường" (learn) attempt or a
 * "Thư viện" (library) section attempt, normalized to the same shape so the FE can render one
 * time-sorted list tagged by {@link #source}. {@code sectionId}/{@code topicId} are only populated
 * for {@code LIBRARY} rows; learn rows leave both {@code null} since a learn attempt isn't tied to
 * a library section. {@code topicId} is the FE's "Làm lại" deep-link navigation target (resolved
 * from {@code sectionId} via {@code ListeningLibraryService#resolveTopicId} since the underlying
 * attempt only carries {@code sectionId}) - {@code sectionId} itself is kept for reference/debugging.
 */
@Getter
@Builder
public class ListeningHistoryEntryDto {
	private String source; // "LEARN" or "LIBRARY"
	private Long attemptOrSessionId;
	private Instant completedAt;
	private Double score;
	private Long sectionId;
	private Long topicId;
}
