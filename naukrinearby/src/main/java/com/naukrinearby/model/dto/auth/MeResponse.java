package com.naukrinearby.model.dto.auth;

import com.naukrinearby.model.entity.User;

public record MeResponse(Long id, String role, String phone, String name) {

	public static MeResponse from(User user) {
		return new MeResponse(user.getId(), user.getRole().name(), user.getPhone(), user.getName());
	}
}
