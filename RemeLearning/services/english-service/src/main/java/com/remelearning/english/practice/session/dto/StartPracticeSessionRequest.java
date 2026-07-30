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
	/**
	 * Exam style every exercise in the session is generated for (see {@code ExamTypes}: TOEIC, IELTS,
	 * TOEFL, VSTEP, General). Optional - null means no exam in mind, which is what the session did
	 * unconditionally before this field existed.
	 */
	private String examType;
}
