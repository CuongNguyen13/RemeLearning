package com.remelearning.common.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/** Standard REST response envelope returned by every service's controllers. */
@Getter
@Builder
public class ApiResponse<T> {

	private boolean success;
	private T data;
	private String errorCode;
	private String message;
	@Builder.Default
	private Instant timestamp = Instant.now();

	public static <T> ApiResponse<T> ok(T data) {
		return ApiResponse.<T>builder().success(true).data(data).build();
	}

	public static <T> ApiResponse<T> error(String errorCode, String message) {
		return ApiResponse.<T>builder().success(false).errorCode(errorCode).message(message).build();
	}
}
