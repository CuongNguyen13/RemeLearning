package com.remelearning.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;
	private final HttpStatus httpStatus;

	public BusinessException(ErrorCode errorCode, String message, HttpStatus httpStatus) {
		super(message);
		this.errorCode = errorCode;
		this.httpStatus = httpStatus;
	}

	public static BusinessException notFound(String message) {
		return new BusinessException(ErrorCode.NOT_FOUND, message, HttpStatus.NOT_FOUND);
	}

	public static BusinessException badRequest(String message) {
		return new BusinessException(ErrorCode.VALIDATION_ERROR, message, HttpStatus.BAD_REQUEST);
	}

	public static BusinessException unauthorized(String message) {
		return new BusinessException(ErrorCode.UNAUTHORIZED, message, HttpStatus.UNAUTHORIZED);
	}

	public static BusinessException forbidden(String message) {
		return new BusinessException(ErrorCode.FORBIDDEN, message, HttpStatus.FORBIDDEN);
	}

	public static BusinessException conflict(String message) {
		return new BusinessException(ErrorCode.CONFLICT, message, HttpStatus.CONFLICT);
	}
}
