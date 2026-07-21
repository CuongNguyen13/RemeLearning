package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

/** The distinct dictation-library filter values, proxied from english-service. */
@Data
public class DictationFacetsDto {
	private List<String> skills;
	private List<String> levels;
	private List<String> topics;
	private List<String> examTypes;
	private int minListensForHint;
}
