package com.remelearning.english.dictation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One distinct clip folder and how many library clips live in it. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FolderCount {
	private String folder;
	private int count;
}
