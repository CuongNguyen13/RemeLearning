package com.remelearning.english.dictation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One recorded audio clip in the fixed dictation library, imported from disk/cloud by
 * {@link com.remelearning.english.dictation.importer.DictationLibraryImporter}. Tagged with the
 * taxonomy the UI filters by (skill / level / topic / examType); {@code scriptText} is the reference
 * transcript graded against, and {@code storageKey} locates the audio in the active StorageClient.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictationClip {
	private Long id;
	private String code;
	private String title;
	private String skill;
	private String level;
	private String topic;
	private String examType;
	private String scriptText;
	private String storageKey;
	private String source;
	private String folder;
	private Instant createdAt;
}
