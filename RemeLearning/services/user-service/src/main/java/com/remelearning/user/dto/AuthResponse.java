package com.remelearning.user.dto;

/** Returned by register/login: the signed JWT plus the authenticated user's profile. */
public record AuthResponse(
		String token,
		UserResponse user) {
}
