package com.remelearning.user.service.impl;

import com.remelearning.common.exception.BusinessException;
import com.remelearning.common.security.JwtTokenProvider;
import com.remelearning.common.storage.S3StorageClient;
import com.remelearning.user.domain.User;
import com.remelearning.user.dto.AuthResponse;
import com.remelearning.user.dto.LoginRequest;
import com.remelearning.user.dto.RegisterRequest;
import com.remelearning.user.dto.UpdateProfileRequest;
import com.remelearning.user.dto.UserResponse;
import com.remelearning.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

	private static final String USER_PHOTO_BUCKET = "reme-user-photos-test";

	private final UserMapper userMapper = mock(UserMapper.class);
	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
	private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
	private final S3StorageClient s3StorageClient = mock(S3StorageClient.class);
	private final UserServiceImpl service =
			new UserServiceImpl(userMapper, passwordEncoder, jwtTokenProvider, s3StorageClient, USER_PHOTO_BUCKET);

	@Test
	void registerInsertsUserAndIssuesToken() {
		when(userMapper.findByEmail("learner@test.com")).thenReturn(Optional.empty());
		when(passwordEncoder.encode("password1")).thenReturn("hashed");
		when(jwtTokenProvider.generateToken(anyString(), anyMap())).thenReturn("jwt-token");

		RegisterRequest request = new RegisterRequest();
		request.setEmail("learner@test.com");
		request.setPassword("password1");
		request.setName("Learner One");

		AuthResponse response = service.register(request);

		assertThat(response.token()).isEqualTo("jwt-token");
		assertThat(response.user().email()).isEqualTo("learner@test.com");
		assertThat(response.user().name()).isEqualTo("Learner One");
		assertThat(response.user().role()).isEqualTo("LEARNER");
		assertThat(response.user().userId()).isNotBlank();

		verify(userMapper, times(1)).insert(any(User.class));
		verify(jwtTokenProvider, times(1)).generateToken(anyString(), anyMap());
	}

	@Test
	void registerWithExistingEmailThrowsConflict() {
		when(userMapper.findByEmail("dup@test.com")).thenReturn(Optional.of(User.builder().email("dup@test.com").build()));

		RegisterRequest request = new RegisterRequest();
		request.setEmail("dup@test.com");
		request.setPassword("password1");
		request.setName("Dup User");

		assertThatThrownBy(() -> service.register(request))
				.isInstanceOf(BusinessException.class)
				.satisfies(ex -> assertThat(((BusinessException) ex).getHttpStatus().value()).isEqualTo(409));

		verify(userMapper, never()).insert(any());
	}

	@Test
	void loginWithMatchingPasswordIssuesToken() {
		User user = User.builder()
				.userId("user-1")
				.email("learner@test.com")
				.passwordHash("hashed")
				.name("Learner One")
				.role("LEARNER")
				.createdAt(Instant.now())
				.build();
		when(userMapper.findByEmail("learner@test.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);
		when(jwtTokenProvider.generateToken(eq("user-1"), anyMap())).thenReturn("jwt-token");

		LoginRequest request = new LoginRequest();
		request.setEmail("learner@test.com");
		request.setPassword("password1");

		AuthResponse response = service.login(request);

		assertThat(response.token()).isEqualTo("jwt-token");
		assertThat(response.user().userId()).isEqualTo("user-1");
	}

	@Test
	void loginWithUnknownEmailThrowsUnauthorized() {
		when(userMapper.findByEmail("missing@test.com")).thenReturn(Optional.empty());

		LoginRequest request = new LoginRequest();
		request.setEmail("missing@test.com");
		request.setPassword("password1");

		assertThatThrownBy(() -> service.login(request))
				.isInstanceOf(BusinessException.class)
				.hasMessage("Invalid email or password");
	}

	@Test
	void loginWithWrongPasswordThrowsSameUnauthorizedAsUnknownEmail() {
		User user = User.builder()
				.userId("user-1")
				.email("learner@test.com")
				.passwordHash("hashed")
				.build();
		when(userMapper.findByEmail("learner@test.com")).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("wrong-password", "hashed")).thenReturn(false);

		LoginRequest request = new LoginRequest();
		request.setEmail("learner@test.com");
		request.setPassword("wrong-password");

		// Deliberate security property: wrong-password and unknown-email must throw the exact
		// same exception type + message, so a caller can't distinguish which one failed.
		assertThatThrownBy(() -> service.login(request))
				.isInstanceOf(BusinessException.class)
				.hasMessage("Invalid email or password");
	}

	@Test
	void getByUserIdThrowsNotFoundWhenMissing() {
		when(userMapper.findByUserId("missing")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getByUserId("missing"))
				.isInstanceOf(BusinessException.class)
				.satisfies(ex -> assertThat(((BusinessException) ex).getHttpStatus().value()).isEqualTo(404));
	}

	@Test
	void getByUserIdReturnsResponseWhenFound() {
		User user = User.builder().userId("user-1").email("learner@test.com").name("Learner One").role("LEARNER").build();
		when(userMapper.findByUserId("user-1")).thenReturn(Optional.of(user));

		UserResponse response = service.getByUserId("user-1");

		assertThat(response.userId()).isEqualTo("user-1");
		assertThat(response.email()).isEqualTo("learner@test.com");
	}

	@Test
	void updateProfileUpdatesNameAndReturnsRefreshedResponse() {
		User before = User.builder().userId("user-1").email("learner@test.com").name("Old Name").role("LEARNER").build();
		User after = User.builder().userId("user-1").email("learner@test.com").name("New Name").role("LEARNER").build();
		when(userMapper.findByUserId("user-1")).thenReturn(Optional.of(before), Optional.of(after));

		UpdateProfileRequest request = new UpdateProfileRequest();
		request.setName("New Name");

		UserResponse response = service.updateProfile("user-1", request);

		assertThat(response.name()).isEqualTo("New Name");
		verify(userMapper, times(1)).updateName("user-1", "New Name");
	}

	@Test
	void updateProfileThrowsNotFoundWhenUserMissing() {
		when(userMapper.findByUserId("missing")).thenReturn(Optional.empty());

		UpdateProfileRequest request = new UpdateProfileRequest();
		request.setName("New Name");

		assertThatThrownBy(() -> service.updateProfile("missing", request))
				.isInstanceOf(BusinessException.class);

		verify(userMapper, never()).updateName(any(), any());
	}

	@Test
	void uploadPhotoStoresToS3AndPersistsUrl() {
		User before = User.builder().userId("user-1").email("learner@test.com").name("Learner One").role("LEARNER").build();
		User after = User.builder().userId("user-1").email("learner@test.com").name("Learner One").role("LEARNER")
				.photoUrl("https://s3.example.com/reme-user-photos-test/user-1/photo/face.jpg").build();
		when(userMapper.findByUserId("user-1")).thenReturn(Optional.of(before), Optional.of(after));
		when(s3StorageClient.objectUrl(eq(USER_PHOTO_BUCKET), anyString()))
				.thenReturn("https://s3.example.com/reme-user-photos-test/user-1/photo/face.jpg");

		MultipartFile file = new MockMultipartFile("file", "face.jpg", "image/jpeg", "fake-bytes".getBytes());

		UserResponse response = service.uploadPhoto("user-1", file);

		assertThat(response.photoUrl()).isEqualTo("https://s3.example.com/reme-user-photos-test/user-1/photo/face.jpg");
		verify(s3StorageClient, times(1))
				.upload(eq(USER_PHOTO_BUCKET), eq("user-1/photo/face.jpg"), any(InputStream.class), anyLong());
		verify(userMapper, times(1)).updatePhoto(eq("user-1"), eq("user-1/photo/face.jpg"), anyString());
	}

	@Test
	void uploadPhotoThrowsNotFoundWhenUserMissing() {
		when(userMapper.findByUserId("missing")).thenReturn(Optional.empty());

		MultipartFile file = new MockMultipartFile("file", "face.jpg", "image/jpeg", "fake-bytes".getBytes());

		assertThatThrownBy(() -> service.uploadPhoto("missing", file))
				.isInstanceOf(BusinessException.class);

		verify(s3StorageClient, never()).upload(anyString(), anyString(), any(InputStream.class), anyLong());
	}

	@Test
	void uploadPhotoRejectsEmptyFile() {
		User user = User.builder().userId("user-1").build();
		when(userMapper.findByUserId("user-1")).thenReturn(Optional.of(user));

		MultipartFile emptyFile = new MockMultipartFile("file", "face.jpg", "image/jpeg", new byte[0]);

		assertThatThrownBy(() -> service.uploadPhoto("user-1", emptyFile))
				.isInstanceOf(BusinessException.class);

		verify(s3StorageClient, never()).upload(anyString(), anyString(), any(InputStream.class), anyLong());
	}
}
