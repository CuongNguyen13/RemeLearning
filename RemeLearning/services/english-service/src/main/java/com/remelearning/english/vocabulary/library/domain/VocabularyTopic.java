package com.remelearning.english.vocabulary.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One row in {@code vocabulary_topics} — a fixed, seeded subject area (e.g. "Travel"). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyTopic {
	private Long id;
	private String code;
	private String name;
	private String description;
	private String level;
	private Instant createdAt;
}
