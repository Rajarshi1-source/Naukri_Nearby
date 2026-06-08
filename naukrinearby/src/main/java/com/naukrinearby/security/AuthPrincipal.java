package com.naukrinearby.security;

/** Authenticated principal placed in the SecurityContext by {@link JwtAuthFilter}. */
public record AuthPrincipal(Long id, String role, String phone) {
}
