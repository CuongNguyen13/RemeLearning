package com.remelearning.bff.dto;

import lombok.Data;

import java.time.Instant;

/** Mirrors user-service's UserResponse: a learner's profile as returned by the auth/user endpoints. */
@Data
public class UserDto {

	private String userId;
	private String email;
	private String name;
	private String role;
	private Instant createdAt;
}
