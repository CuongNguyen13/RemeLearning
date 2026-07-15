package com.remelearning.bff.controller;

import com.remelearning.bff.client.UserServiceClient;
import com.remelearning.bff.dto.AuthResponseDto;
import com.remelearning.bff.dto.LoginRequestDto;
import com.remelearning.bff.dto.RegisterRequestDto;
import com.remelearning.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Tag(name = "Auth", description = "Proxy for user-service's registration/login endpoints")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final UserServiceClient userServiceClient;

	@Operation(summary = "Register a new account; thin proxy to user-service")
	@PostMapping("/register")
	public Mono<ApiResponse<AuthResponseDto>> register(@RequestBody RegisterRequestDto request) {
		return userServiceClient.register(request).map(ApiResponse::ok);
	}

	@Operation(summary = "Log in with email/password; thin proxy to user-service")
	@PostMapping("/login")
	public Mono<ApiResponse<AuthResponseDto>> login(@RequestBody LoginRequestDto request) {
		return userServiceClient.login(request).map(ApiResponse::ok);
	}
}
