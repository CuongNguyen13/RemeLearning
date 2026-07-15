package com.remelearning.bff.dto;

import lombok.Data;

/** Mirrors user-service's AuthResponse: the JWT issued on register/login plus the user's profile. */
@Data
public class AuthResponseDto {

	private String token;
	private UserDto user;
}
