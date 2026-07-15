package com.remelearning.bff.dto;

import lombok.Data;

/** Pass-through request body for user-service's POST /api/v1/auth/login; not re-validated here. */
@Data
public class LoginRequestDto {

	private String email;
	private String password;
}
