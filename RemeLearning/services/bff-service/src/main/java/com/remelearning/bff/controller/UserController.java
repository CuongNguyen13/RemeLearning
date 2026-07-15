package com.remelearning.bff.controller;

import com.remelearning.bff.client.UserServiceClient;
import com.remelearning.bff.dto.UpdateProfileRequestDto;
import com.remelearning.bff.dto.UserDto;
import com.remelearning.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Tag(name = "Users", description = "Proxy for user-service's profile endpoints")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

	private final UserServiceClient userServiceClient;

	@Operation(summary = "Fetch a user's profile by id; thin proxy to user-service")
	@GetMapping("/{userId}")
	public Mono<ApiResponse<UserDto>> getByUserId(@PathVariable String userId) {
		return userServiceClient.getByUserId(userId).map(ApiResponse::ok);
	}

	@Operation(summary = "Update a user's profile (name); thin proxy to user-service")
	@PatchMapping("/{userId}")
	public Mono<ApiResponse<UserDto>> updateProfile(@PathVariable String userId, @RequestBody UpdateProfileRequestDto request) {
		return userServiceClient.updateProfile(userId, request).map(ApiResponse::ok);
	}
}
