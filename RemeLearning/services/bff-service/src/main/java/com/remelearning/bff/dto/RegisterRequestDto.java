package com.remelearning.bff.dto;

import lombok.Data;

/** Pass-through request body for user-service's POST /api/v1/auth/register; not re-validated here. */
@Data
public class RegisterRequestDto {

	private String email;
	private String password;
	private String name;
}
