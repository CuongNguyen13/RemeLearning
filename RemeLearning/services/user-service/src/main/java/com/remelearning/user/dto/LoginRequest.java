package com.remelearning.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Payload for POST /api/v1/auth/login. */
@Data
public class LoginRequest {
	@NotBlank
	@Email
	private String email;

	@NotBlank
	private String password;
}
