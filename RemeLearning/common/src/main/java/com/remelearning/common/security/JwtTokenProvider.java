package com.remelearning.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/** Issues and validates HMAC-signed JWTs shared by all services for authentication. */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

	private final JwtProperties jwtProperties;

	private SecretKey signingKey() {
		return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
	}

	/** Issues a signed token for {@code subject} (typically the user id) carrying {@code claims}. */
	public String generateToken(String subject, Map<String, Object> claims) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(subject)
				.claims(claims)
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(Duration.ofMinutes(jwtProperties.getExpirationMinutes()))))
				.signWith(signingKey())
				.compact();
	}

	/** Verifies the signature/expiry and returns the token's claims; throws if invalid. */
	public Claims parseClaims(String token) {
		return Jwts.parser()
				.verifyWith(signingKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	public boolean isValid(String token) {
		try {
			parseClaims(token);
			return true;
		} catch (Exception ex) {
			return false;
		}
	}
}
