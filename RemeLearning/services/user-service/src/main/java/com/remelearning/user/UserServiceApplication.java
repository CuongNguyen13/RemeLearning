package com.remelearning.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Entry point for user-service: authentication (register/login issuing JWTs) and basic user
 * profile CRUD. Scans com.remelearning broadly (not just this service's own package) so shared
 * beans from the common module - e.g. GlobalExceptionHandler, JwtTokenProvider - are picked up.
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.remelearning")
@MapperScan("com.remelearning.user.mapper")
public class UserServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(UserServiceApplication.class, args);
	}
}
