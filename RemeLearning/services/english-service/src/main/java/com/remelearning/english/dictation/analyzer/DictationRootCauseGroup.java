package com.remelearning.english.dictation.analyzer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** A root-cause explanation for one {@link DictationErrorCategory}, only present when it has misses. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DictationRootCauseGroup {
	private DictationErrorCategory category;
	private String summary;
	private List<String> examples;
}
