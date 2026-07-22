package com.remelearning.english.vocabulary.library.domain;

import com.remelearning.english.vocabulary.domain.VocabularyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One row in {@code vocabulary_library_words} — a single word/phrase in a topic's word bank. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocabularyLibraryWord {
	private Long id;
	private Long topicId;
	private String word;
	private VocabularyType wordType;
	private String meaningVi;
	private String exampleEn;
	private String ipa;
	private String audioStorageKey;
	private Instant createdAt;
}
