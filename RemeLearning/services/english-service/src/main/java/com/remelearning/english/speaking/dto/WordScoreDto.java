package com.remelearning.english.speaking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** One scored word, proxied from ai-service's GOP result - persisted as JSON in {@code speaking_attempts.word_scores}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WordScoreDto {
	private String word;
	private double score;
	private List<PhonemeScoreDto> phonemes;
}
