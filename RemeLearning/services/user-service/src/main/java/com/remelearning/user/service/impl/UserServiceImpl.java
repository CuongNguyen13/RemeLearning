package com.remelearning.user.service.impl;

import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.exception.ErrorCode;
import com.remelearning.common.security.JwtTokenProvider;
import com.remelearning.common.storage.S3StorageClient;
import com.remelearning.user.domain.User;
import com.remelearning.user.domain.UserRole;
import com.remelearning.user.dto.AuthResponse;
import com.remelearning.user.dto.LoginRequest;
import com.remelearning.user.dto.RegisterRequest;
import com.remelearning.user.dto.UpdateProfileRequest;
import com.remelearning.user.dto.UserResponse;
import com.remelearning.user.mapper.UserMapper;
import com.remelearning.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

	// Same message used for "no such email" and "wrong password" so a caller can't tell which
	// one failed - a deliberate security property, not an oversight.
	private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenProvider jwtTokenProvider;
	private final S3StorageClient s3StorageClient;
	private final String userPhotoBucket;

	// Constructor injection (incl. the reme.s3.user-photo-bucket property) rather than field
	// injection/@RequiredArgsConstructor, so this class can be constructed directly with mocks
	// in unit tests - same reasoning as RecordingServiceImpl's constructor.
	public UserServiceImpl(
			UserMapper userMapper,
			PasswordEncoder passwordEncoder,
			JwtTokenProvider jwtTokenProvider,
			S3StorageClient s3StorageClient,
			@Value("${reme.s3.user-photo-bucket}") String userPhotoBucket) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenProvider = jwtTokenProvider;
		this.s3StorageClient = s3StorageClient;
		this.userPhotoBucket = userPhotoBucket;
	}

	// Rejects duplicate emails, hashes the password, persists a new LEARNER account with a fresh
	// UUID userId, then issues a JWT so the caller is immediately authenticated post-registration.
	@Override
	@Transactional
	public AuthResponse register(RegisterRequest request) {
		if (userMapper.findByEmail(request.getEmail()).isPresent()) {
			throw BusinessException.conflict("Email already registered: " + request.getEmail());
		}

		String userId = UUID.randomUUID().toString();
		String role = UserRole.LEARNER.name();
		User user = User.builder()
				.userId(userId)
				.email(request.getEmail())
				.passwordHash(passwordEncoder.encode(request.getPassword()))
				.name(request.getName())
				.role(role)
				.build();
		userMapper.insert(user);

		log.info("Registered new user {} ({})", userId, request.getEmail());
		String token = issueToken(userId, request.getEmail(), role);
		return new AuthResponse(token, toResponse(user));
	}

	// Verifies email + password against the stored hash; throws the same unauthorized error
	// regardless of whether the email is unknown or the password is wrong (see
	// INVALID_CREDENTIALS_MESSAGE), then issues a fresh JWT on success.
	@Override
	public AuthResponse login(LoginRequest request) {
		User user = userMapper.findByEmail(request.getEmail())
				.filter(u -> passwordEncoder.matches(request.getPassword(), u.getPasswordHash()))
				.orElseThrow(() -> BusinessException.unauthorized(INVALID_CREDENTIALS_MESSAGE));

		String token = issueToken(user.getUserId(), user.getEmail(), user.getRole());
		return new AuthResponse(token, toResponse(user));
	}

	// Loads a single user's public profile by userId, or throws a 404-mapped BusinessException
	// if none was ever stored.
	@Override
	public UserResponse getByUserId(String userId) {
		User user = findUserOrThrow(userId);
		return toResponse(user);
	}

	// Updates just the display name for an existing user, then returns the refreshed profile.
	@Override
	@Transactional
	public UserResponse updateProfile(String userId, UpdateProfileRequest request) {
		findUserOrThrow(userId);
		userMapper.updateName(userId, request.getName());
		return toResponse(findUserOrThrow(userId));
	}

	// Uploads a new/replacement profile photo to S3 under a userId/photo/filename key and
	// persists its key + public URL, so ai-service can later fetch it (GET /api/v1/users/
	// {userId}, via photoUrl) to enroll a face-recognition reference embedding. Mirrors
	// RecordingServiceImpl.upload's exact S3-then-persist pattern.
	@Override
	@Transactional
	public UserResponse uploadPhoto(String userId, MultipartFile file) {
		findUserOrThrow(userId);
		if (file == null || file.isEmpty()) {
			throw BusinessException.badRequest("file must not be empty");
		}

		String s3Key = userId + "/photo/" + file.getOriginalFilename();
		try {
			s3StorageClient.upload(userPhotoBucket, s3Key, file.getInputStream(), file.getSize());
		} catch (Exception e) {
			log.error("Failed to upload photo for user {} to s3://{}/{}", userId, userPhotoBucket, s3Key, e);
			throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR,
					"Failed to upload photo to S3", HttpStatus.BAD_GATEWAY);
		}

		String photoUrl = s3StorageClient.objectUrl(userPhotoBucket, s3Key);
		userMapper.updatePhoto(userId, s3Key, photoUrl);

		log.info("Stored photo for user {} at s3://{}/{}", userId, userPhotoBucket, s3Key);
		return toResponse(findUserOrThrow(userId));
	}

	private User findUserOrThrow(String userId) {
		return userMapper.findByUserId(userId)
				.orElseThrow(() -> BusinessException.notFound("User not found for userId=" + userId));
	}

	private String issueToken(String userId, String email, String role) {
		return jwtTokenProvider.generateToken(userId, Map.of("email", email, "role", role));
	}

	private UserResponse toResponse(User user) {
		return new UserResponse(
				user.getUserId(), user.getEmail(), user.getName(), user.getRole(), user.getPhotoUrl(), user.getCreatedAt());
	}
}
