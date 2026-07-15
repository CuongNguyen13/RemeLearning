package com.remelearning.bff.dto;

import lombok.Data;

/** Pass-through request body for user-service's PATCH /api/v1/users/{userId}; not re-validated here. */
@Data
public class UpdateProfileRequestDto {

	private String name;
}
