package com.remelearning.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Payload for PATCH /api/v1/users/{userId}. */
@Data
public class UpdateProfileRequest {
	@NotBlank
	private String name;
}
