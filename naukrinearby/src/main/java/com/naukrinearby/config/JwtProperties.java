package com.naukrinearby.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naukri.jwt")
public record JwtProperties(
		String issuer,
		Duration accessTtl,
		Duration refreshTtl,
		String privateKey,
		String publicKey) {

	public JwtProperties {
		if (accessTtl == null) {
			accessTtl = Duration.ofMinutes(15);
		}
		if (refreshTtl == null) {
			refreshTtl = Duration.ofDays(30);
		}
	}
}
