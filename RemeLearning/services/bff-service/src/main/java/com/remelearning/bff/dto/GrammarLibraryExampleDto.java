package com.remelearning.bff.dto;

import lombok.Data;

/** One EN/VI example sentence pair shown on a grammar-library topic's theory page. */
@Data
public class GrammarLibraryExampleDto {
	private String en;
	private String vi;
}
