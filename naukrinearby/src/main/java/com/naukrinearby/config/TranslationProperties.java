package com.naukrinearby.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Translation provider config (master plan §2). Default provider is {@code stub} (passthrough);
 * set {@code naukri.translation.provider=bhashini} with a {@code base-url}/{@code api-key} to call a
 * real Bhashini-compatible endpoint.
 */
@ConfigurationProperties(prefix = "naukri.translation")
public record TranslationProperties(
		String provider,
		String baseUrl,
		String apiKey,
		Duration timeout) {

	public TranslationProperties {
		if (provider == null || provider.isBlank()) {
			provider = "stub";
		}
		if (timeout == null) {
			timeout = Duration.ofSeconds(10);
		}
	}
}
