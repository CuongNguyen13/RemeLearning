package com.remelearning.bff.client;

import com.remelearning.bff.dto.AuthResponseDto;
import com.remelearning.bff.dto.LoginRequestDto;
import com.remelearning.bff.dto.RegisterRequestDto;
import com.remelearning.bff.dto.UpdateProfileRequestDto;
import com.remelearning.bff.dto.UserDto;
import com.remelearning.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** Thin wrapper around user-service's auth + profile REST API. */
@Slf4j
@Component
public class UserServiceClient {

	private final WebClient userServiceClient;

	public UserServiceClient(@Qualifier("userServiceClient") WebClient userServiceClient) {
		this.userServiceClient = userServiceClient;
	}

	/** Registers a new account and returns the issued token + profile. */
	public Mono<AuthResponseDto> register(RegisterRequestDto request) {
		return userServiceClient.post()
				.uri("/api/v1/auth/register")
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<AuthResponseDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to register user with email={}", request.getEmail(), ex));
	}

	/** Authenticates an existing account and returns the issued token + profile. */
	public Mono<AuthResponseDto> login(LoginRequestDto request) {
		return userServiceClient.post()
				.uri("/api/v1/auth/login")
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<AuthResponseDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to login user with email={}", request.getEmail(), ex));
	}

	/** Fetches a user's profile by id. */
	public Mono<UserDto> getByUserId(String userId) {
		return userServiceClient.get()
				.uri("/api/v1/users/{userId}", userId)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<UserDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to fetch user for userId={}", userId, ex));
	}

	/** Updates a user's profile (currently just the display name). */
	public Mono<UserDto> updateProfile(String userId, UpdateProfileRequestDto request) {
		return userServiceClient.patch()
				.uri("/api/v1/users/{userId}", userId)
				.bodyValue(request)
				.retrieve()
				.bodyToMono(new ParameterizedTypeReference<ApiResponse<UserDto>>() {})
				.map(ApiResponse::getData)
				.doOnError(ex -> log.error("Failed to update profile for userId={}", userId, ex));
	}
}
