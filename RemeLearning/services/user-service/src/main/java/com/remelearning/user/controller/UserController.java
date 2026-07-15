package com.remelearning.user.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.user.dto.UpdateProfileRequest;
import com.remelearning.user.dto.UserResponse;
import com.remelearning.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "Basic user profile lookup and update")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@Operation(summary = "Get a single user's public profile by their userId")
	@GetMapping("/{userId}")
	public ApiResponse<UserResponse> getByUserId(@PathVariable String userId) {
		return ApiResponse.ok(userService.getByUserId(userId));
	}

	@Operation(summary = "Update a user's display name")
	@PatchMapping("/{userId}")
	public ApiResponse<UserResponse> updateProfile(
			@PathVariable String userId, @Valid @RequestBody UpdateProfileRequest request) {
		return ApiResponse.ok(userService.updateProfile(userId, request));
	}
}
