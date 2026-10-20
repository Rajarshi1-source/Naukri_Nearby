package com.naukrinearby.security;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Decides which requests need a CSRF token (security-and-api.md §A.5c).
 *
 * <p>CSRF is an attack on <em>ambient</em> credentials: ones the browser attaches to a cross-site
 * request by itself. In this API the only credential is a JWT in the {@code Authorization} header,
 * which a cross-site form, image or script cannot set, so header-authenticated requests cannot be
 * forged. The only ambient credential Spring would honour is a live servlet session, so a
 * state-changing request that presents a valid session must carry a valid CSRF token.
 *
 * <p>A session cookie that names no live session (stale, forged, or set on {@code localhost} by another
 * local app — cookies ignore ports) is not a credential and does not trigger the check; otherwise such
 * a stray cookie would break every POST from the web app. Today the API is stateless and never creates
 * a session, so legitimate clients never match. If cookie or session authentication is ever added,
 * CSRF enforcement is already on for it instead of silently off.
 */
public final class AmbientCredentialCsrfMatcher implements RequestMatcher {

	/** Methods that must not change state (RFC 9110 §9.2.1) never need a token. */
	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

	@Override
	public boolean matches(HttpServletRequest request) {
		if (SAFE_METHODS.contains(request.getMethod())) {
			return false;
		}
		return request.isRequestedSessionIdValid() || request.getSession(false) != null;
	}
}
