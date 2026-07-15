package com.remelearning.user.dto;

import java.time.Instant;

/** Public-facing user profile; never carries the password/passwordHash. */
public record UserResponse(
		String userId,
		String email,
		String name,
		String role,
		Instant createdAt) {
}
