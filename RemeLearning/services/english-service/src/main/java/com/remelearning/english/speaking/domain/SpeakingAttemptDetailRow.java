package com.remelearning.english.speaking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakingAttemptDetailRow {
	private Long attemptId;
	private String level;
	private String examType;
	private String topic;
	private String targetText;
	private double overallScore;
	private String wordScoresJson;
	private String transcript;
	private String weakPhonemesJson;
	private Instant createdAt;
}
