package com.naukrinearby.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naukri.otp")
public record OtpProperties(
		String provider,
		String devCode,
		Duration ttl,
		int maxPerHour) {

	public OtpProperties {
		if (provider == null || provider.isBlank()) {
			provider = "stub";
		}
		if (devCode == null || devCode.isBlank()) {
			devCode = "123456";
		}
		if (ttl == null) {
			ttl = Duration.ofMinutes(5);
		}
		if (maxPerHour <= 0) {
			maxPerHour = 5;
		}
	}
}
