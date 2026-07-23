package com.remelearning.english.speaking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One scored phoneme (IPA symbol), proxied from ai-service's GOP result. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhonemeScoreDto {
	private String ipa;
	private double score;
}
