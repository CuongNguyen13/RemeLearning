package com.remelearning.common.web;

import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.exception.ErrorCode;
import com.remelearning.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized exception-to-HTTP mapping applied to every service that includes {@code common}.
 * Ensures all failures come back as a consistent {@link ApiResponse} envelope.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/** Expected domain failures: mapped straight to the status/code carried by the exception. */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
		return ResponseEntity.status(ex.getHttpStatus())
				.body(ApiResponse.error(ex.getErrorCode().name(), ex.getMessage()));
	}

	/** Bean validation failures on @Valid request bodies; surfaces the first field error. */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.findFirst()
				.map(FieldError::getDefaultMessage)
				.orElse("Validation failed");
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ApiResponse.error(ErrorCode.VALIDATION_ERROR.name(), message));
	}

	/** Catch-all for unanticipated errors; logged in full but hidden from the client response. */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception ex) {
		log.error("Unhandled exception", ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.name(), "Internal server error"));
	}
}
