package com.remelearning.user.mapper;

import com.remelearning.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserMapper {

	/** Sets the generated id back onto {@code user}. */
	void insert(User user);

	/** Looks up one user by their public userId (UUID), or empty if none exists. */
	Optional<User> findByUserId(@Param("userId") String userId);

	/** Looks up one user by email (used for login and duplicate-registration checks). */
	Optional<User> findByEmail(@Param("email") String email);

	/** Updates just the display name for a given userId. */
	void updateName(@Param("userId") String userId, @Param("name") String name);

	/** Updates the S3 key + public URL of a user's profile photo for a given userId. */
	void updatePhoto(@Param("userId") String userId, @Param("photoS3Key") String photoS3Key, @Param("photoUrl") String photoUrl);
}
