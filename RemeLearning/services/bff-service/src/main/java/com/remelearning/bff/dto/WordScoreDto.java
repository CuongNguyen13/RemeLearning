package com.remelearning.bff.dto;

import lombok.Data;

import java.util.List;

@Data
public class WordScoreDto {
	private String word;
	private double score;
	private List<PhonemeScoreDto> phonemes;
}
