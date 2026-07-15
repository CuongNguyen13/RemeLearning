package com.remelearning.user.service;

import com.remelearning.user.dto.AuthResponse;
import com.remelearning.user.dto.LoginRequest;
import com.remelearning.user.dto.RegisterRequest;
import com.remelearning.user.dto.UpdateProfileRequest;
import com.remelearning.user.dto.UserResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Authentication (register/login, issuing JWTs) and basic profile management. Callers (the
 * controllers) depend on this interface, not {@code UserServiceImpl}, so the persistence/
 * password-hashing/token-issuing strategy can change later without touching them.
 */
public interface UserService {

	AuthResponse register(RegisterRequest request);

	AuthResponse login(LoginRequest request);

	UserResponse getByUserId(String userId);

	UserResponse updateProfile(String userId, UpdateProfileRequest request);

	/** Uploads/replaces a user's profile photo to S3 and persists its key/URL, so
	 * ai-service can later fetch it for face-recognition enrollment. */
	UserResponse uploadPhoto(String userId, MultipartFile file);
}
