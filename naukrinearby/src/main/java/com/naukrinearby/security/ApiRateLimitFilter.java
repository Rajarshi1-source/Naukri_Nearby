package com.naukrinearby.security;

import java.io.IOException;
import java.time.Duration;

import tools.jackson.databind.ObjectMapper;
import com.naukrinearby.util.RedisRateLimiter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window API rate limit (default 100 req/min) keyed by authenticated user id, falling back to
 * client IP for anonymous traffic (security-and-api.md §A.4). Runs after {@link JwtAuthFilter} so the
 * principal is available. Auth, webhook, and internal-eval paths are exempt (they have their own
 * limits / are machine-to-machine). Over-limit requests get a 429 {@code ProblemDetail}.
 */
@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {

	private final RedisRateLimiter rateLimiter;
	private final ObjectMapper objectMapper;
	private final int perMinute;

	public ApiRateLimitFilter(RedisRateLimiter rateLimiter, ObjectMapper objectMapper,
			@Value("${naukri.ratelimit.api-per-minute:100}") int perMinute) {
		this.rateLimiter = rateLimiter;
		this.objectMapper = objectMapper;
		this.perMinute = perMinute;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !path.startsWith("/api/")
				|| path.startsWith("/api/auth")
				|| path.startsWith("/api/webhooks")
				|| path.startsWith("/api/internal");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {
		String key = "rl:api:" + callerId(req);
		if (!rateLimiter.tryAcquire(key, perMinute, Duration.ofMinutes(1))) {
			writeTooManyRequests(res);
			return;
		}
		chain.doFilter(req, res);
	}

	private String callerId(HttpServletRequest req) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth != null && auth.getPrincipal() instanceof AuthPrincipal principal) {
			return "user:" + principal.id();
		}
		return "ip:" + req.getRemoteAddr();
	}

	private void writeTooManyRequests(HttpServletResponse res) throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Try again shortly.");
		problem.setTitle("Too Many Requests");
		res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		objectMapper.writeValue(res.getWriter(), problem);
	}
}
