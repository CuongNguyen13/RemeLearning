package com.remelearning.common.exception;

/** Stable machine-readable codes returned in {@link com.remelearning.common.response.ApiResponse} on failure. */
public enum ErrorCode {

	VALIDATION_ERROR,
	NOT_FOUND,
	UNAUTHORIZED,
	FORBIDDEN,
	CONFLICT,
	/** A downstream service/vendor (LLM, STT, S3, ...) failed or timed out. */
	EXTERNAL_SERVICE_ERROR,
	INTERNAL_ERROR
}
