package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** A root-cause explanation for one error category, proxied from english-service. */
@Data
public class DictationRootCauseGroupDto {
	private String category;
	private String summary;
	private List<String> examples;
}
