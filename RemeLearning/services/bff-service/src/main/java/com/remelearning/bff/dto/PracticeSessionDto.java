package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;
import java.util.List;

/** A practice session plus its ordered exercise slots, proxied from english-service. */
@Data
public class PracticeSessionDto {
	private Long sessionId;
	private String status;
	private int totalExercises;
	private List<PracticeSessionExerciseDto> exercises;
	private Instant createdAt;
	private Instant completedAt;
}
