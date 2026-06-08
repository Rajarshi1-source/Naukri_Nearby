package com.naukrinearby.util;

import java.time.Duration;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Simple fixed-window rate limiter on Redis (INCR + EXPIRE on first hit), per security-and-api.md §A.4. */
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

	private final StringRedisTemplate redis;

	/** Returns true if this hit is within the limit; sets the window TTL on the first hit. */
	public boolean tryAcquire(String key, long max, Duration window) {
		Long count = redis.opsForValue().increment(key);
		if (count != null && count == 1L) {
			redis.expire(key, window);
		}
		return count != null && count <= max;
	}
}
