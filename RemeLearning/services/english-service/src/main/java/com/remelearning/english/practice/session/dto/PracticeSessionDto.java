package com.remelearning.english.practice.session.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/** A practice session plus its ordered exercise slots, as returned to the client. */
@Getter
@Builder
public class PracticeSessionDto {
	private Long sessionId;
	private String status;
	private int totalExercises;
	private List<PracticeSessionExerciseDto> exercises;
	private Instant createdAt;
	private Instant completedAt;
}
