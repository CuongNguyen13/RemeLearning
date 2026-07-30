package com.remelearning.english.practice.session.dto;

import lombok.Data;

/**
 * Request to start a practice session. {@code exerciseCount} is optional (defaults to 4); the service
 * clamps it to a sane range. {@code userId} is set by the BFF from the path before forwarding.
 */
@Data
public class StartPracticeSessionRequest {
	private String userId;
	private Integer exerciseCount;
}
