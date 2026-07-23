package com.remelearning.bff.dto;

import lombok.Data;

/** One listening-library catalog topic with this learner's own progression status, proxied from english-service. */
@Data
public class ListeningLibraryTopicDto {
	private Long id;
	private String name;
	private String level;
	private String status;
}
