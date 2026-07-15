package com.remelearning.user.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Persisted account: credentials plus basic profile, one row per registered user. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
	private Long id;
	private String userId;
	private String email;
	private String passwordHash;
	private String name;
	private String role;
	private Instant createdAt;
	private Instant updatedAt;
}
