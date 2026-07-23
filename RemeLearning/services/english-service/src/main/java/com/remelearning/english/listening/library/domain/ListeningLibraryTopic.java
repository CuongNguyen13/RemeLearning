package com.remelearning.english.listening.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One row in {@code listening_library_topics} — a fixed, seeded listening topic (e.g. "Present Simple"). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListeningLibraryTopic {
	private Long id;
	private String code;
	private String name;
	private String description;
	private String level;
	private int sequenceOrder;
	private Instant createdAt;
}
