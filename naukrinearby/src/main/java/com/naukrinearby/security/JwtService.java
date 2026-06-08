package com.naukrinearby.security;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.naukrinearby.config.JwtProperties;
import com.naukrinearby.model.entity.User;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues short-lived RS256 access tokens and opaque refresh tokens. Refresh tokens are stored
 * server-side in Redis (keyed by the token) so they can be rotated and revoked (security-and-api.md §A.2).
 */
@Service
@RequiredArgsConstructor
public class JwtService {

	private static final String REFRESH_PREFIX = "refresh:";

	private final JwtEncoder jwtEncoder;
	private final JwtProperties props;
	private final StringRedisTemplate redis;

	public String issueAccessToken(User user) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(props.issuer())
				.issuedAt(now)
				.expiresAt(now.plus(props.accessTtl()))
				.subject(String.valueOf(user.getId()))
				.id(UUID.randomUUID().toString())
				.claim("role", user.getRole().name())
				.claim("phone", user.getPhone())
				.claim("phone_verified", user.isVerified())
				.build();
		return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
	}

	public String issueRefreshToken(Long userId) {
		String token = UUID.randomUUID().toString();
		redis.opsForValue().set(REFRESH_PREFIX + token, String.valueOf(userId), props.refreshTtl());
		return token;
	}

	/** Returns the userId for a valid refresh token, rotating it (old token invalidated). */
	public Long rotateRefreshToken(String oldToken, String newToken) {
		String key = REFRESH_PREFIX + oldToken;
		String userId = redis.opsForValue().get(key);
		if (userId == null) {
			return null;
		}
		redis.delete(key);
		redis.opsForValue().set(REFRESH_PREFIX + newToken, userId, props.refreshTtl());
		return Long.valueOf(userId);
	}

	public Long resolveRefreshToken(String token) {
		String userId = redis.opsForValue().get(REFRESH_PREFIX + token);
		return userId == null ? null : Long.valueOf(userId);
	}

	public void revokeRefreshToken(String token) {
		redis.delete(REFRESH_PREFIX + token);
	}

	public Duration accessTtl() {
		return props.accessTtl();
	}
}
