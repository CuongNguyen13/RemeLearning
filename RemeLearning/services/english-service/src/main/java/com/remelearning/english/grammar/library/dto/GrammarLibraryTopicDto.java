package com.remelearning.english.grammar.library.dto;

import com.remelearning.english.grammar.library.domain.GrammarTopicStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GrammarLibraryTopicDto {
	private Long topicId;
	private String code;
	private String name;
	private String description;
	private String level;
	private int sequenceOrder;
	private GrammarTopicStatus status;
}
