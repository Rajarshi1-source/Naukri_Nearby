package com.naukrinearby.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Verifies the bearer JWT (signature + exp) and populates the SecurityContext (security-and-api.md §A.2). */
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtDecoder jwtDecoder;

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
			throws ServletException, IOException {
		String header = req.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith("Bearer ")) {
			chain.doFilter(req, res);
			return;
		}
		try {
			Jwt jwt = jwtDecoder.decode(header.substring(7));
			String role = jwt.getClaimAsString("role");
			AuthPrincipal principal = new AuthPrincipal(
					Long.valueOf(jwt.getSubject()), role, jwt.getClaimAsString("phone"));
			var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
			var auth = new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
			SecurityContextHolder.getContext().setAuthentication(auth);
		}
		catch (JwtException | NumberFormatException ex) {
			res.sendError(HttpStatus.UNAUTHORIZED.value(), "Invalid token");
			return;
		}
		chain.doFilter(req, res);
	}
}
