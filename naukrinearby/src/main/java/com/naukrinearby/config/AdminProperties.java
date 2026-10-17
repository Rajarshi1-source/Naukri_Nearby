package com.naukrinearby.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Admin bootstrap: phones listed here (E.164 or 10-digit Indian numbers) receive {@code ROLE_ADMIN}
 * at OTP verification. There is no self-service admin sign-up.
 */
@ConfigurationProperties(prefix = "naukri.admin")
public record AdminProperties(List<String> phones) {

	public AdminProperties {
		phones = phones == null ? List.of() : List.copyOf(phones);
	}
}
