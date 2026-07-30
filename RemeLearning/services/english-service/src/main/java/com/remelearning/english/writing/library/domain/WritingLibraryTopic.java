package com.remelearning.english.writing.library.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One catalogue topic on one taxonomy axis (row in {@code writing_library_topics}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WritingLibraryTopic {
	private Long id;
	/**
	 * The stored lower-case axis code ({@code "grammar"}/{@code "genre"}/{@code "vocab_theme"}), not
	 * the {@link WritingTaxonomy} enum: MyBatis' default enum handler maps by {@code name()}, which is
	 * upper-case and would fail to read these rows. Convert with {@link WritingTaxonomy#fromCode}.
	 */
	private String taxonomy;
	private String code;
	private String name;
	private String description;
	private String level;
	/** Unique within this topic's taxonomy only, restarting at 1 per axis. */
	private Integer sequenceOrder;
	private Instant createdAt;
}
