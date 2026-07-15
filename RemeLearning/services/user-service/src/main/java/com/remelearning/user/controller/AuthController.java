package com.remelearning.user.controller;

import com.remelearning.common.response.ApiResponse;
import com.remelearning.user.dto.AuthResponse;
import com.remelearning.user.dto.LoginRequest;
import com.remelearning.user.dto.RegisterRequest;
import com.remelearning.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Registration and login, issuing JWTs")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

	private final UserService userService;

	@Operation(summary = "Register a new learner account and issue a JWT")
	@PostMapping("/register")
	public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ApiResponse.ok(userService.register(request));
	}

	@Operation(summary = "Authenticate with email + password and issue a fresh JWT")
	@PostMapping("/login")
	public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.ok(userService.login(request));
	}
}
