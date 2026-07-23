package com.remelearning.english.speaking.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One row in {@code speaking_library_topics} — a fixed, seeded speaking topic (e.g. "Present Simple"). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakingLibraryTopic {
	private Long id;
	private String code;
	private String name;
	private String description;
	private String level;
	private int sequenceOrder;
	private Instant createdAt;
}
