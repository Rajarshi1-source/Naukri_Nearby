package com.naukrinearby.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naukri.embedding")
public record EmbeddingProperties(
		String provider,
		String model,
		int dims,
		String baseUrl,
		String apiKey) {

	public EmbeddingProperties {
		if (provider == null || provider.isBlank()) {
			provider = "local";
		}
		if (dims <= 0) {
			dims = 1024;
		}
	}
}
