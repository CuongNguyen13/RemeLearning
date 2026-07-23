package com.remelearning.bff.dto;

import lombok.Data;

/** One grammar-library catalog topic with this learner's own progression status, proxied from english-service. */
@Data
public class GrammarLibraryTopicDto {
	private Long topicId;
	private String code;
	private String name;
	private String description;
	private String level;
	private int sequenceOrder;
	private String status;
}
