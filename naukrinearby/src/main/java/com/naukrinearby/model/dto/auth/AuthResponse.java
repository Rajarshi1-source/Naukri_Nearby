package com.naukrinearby.model.dto.auth;

public record AuthResponse(String accessToken, String refreshToken, String role) {
}
