package com.remelearning.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds the {@code reme.jwt.*} properties consumed by {@link JwtTokenProvider}. */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "reme.jwt")
public class JwtProperties {

	/** HMAC signing secret; every environment must override the placeholder default. */
	private String secret = "change-me-change-me-change-me-change-me";
	private long expirationMinutes = 60;
}
