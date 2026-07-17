package com.remelearning.common.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Standard REST response envelope returned by every service's controllers. Needs a no-args
 * constructor + setters (not just the builder) so bff-service - the only caller that
 * deserializes this back out of downstream JSON instead of just building/serializing it - can
 * construct it: this project is on Jackson 3 (the {@code tools.jackson} package), which Lombok's
 * {@code @Jacksonized} (targets Jackson 2's builder-deserialization annotations) doesn't support.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
