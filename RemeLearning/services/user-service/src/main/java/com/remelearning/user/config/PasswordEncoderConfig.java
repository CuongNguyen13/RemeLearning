package com.remelearning.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Registers a BCrypt PasswordEncoder bean. Deliberately does NOT pull in
 * spring-boot-starter-security (only spring-security-crypto), so no filter chain/default login
 * page gets auto-configured - this service issues JWTs but nothing validates them yet.
 */
@Configuration
public class PasswordEncoderConfig {

	// Single BCrypt-backed PasswordEncoder bean shared by register/login; callers depend on the
	// PasswordEncoder interface, never BCryptPasswordEncoder directly.
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
