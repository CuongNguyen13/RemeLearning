package com.remelearning.common.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "reme.jwt")
public class JwtProperties {

	private String secret = "change-me-change-me-change-me-change-me";
	private long expirationMinutes = 60;
}
