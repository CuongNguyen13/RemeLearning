package com.remelearning.bff.dto;

import lombok.Data;

/** One speaking-library catalog topic with this learner's own progression status, proxied from english-service. */
@Data
public class SpeakingLibraryTopicDto {
	private Long id;
	private String name;
	private String level;
	private String status;
}
