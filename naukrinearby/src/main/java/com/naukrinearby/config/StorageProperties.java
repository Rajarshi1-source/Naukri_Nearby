package com.naukrinearby.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naukri.storage")
public record StorageProperties(
		String endpoint,
		String accessKey,
		String secretKey,
		String bucket,
		String encryption,
		Duration signedUrlTtl) {

	public StorageProperties {
		if (endpoint == null || endpoint.isBlank()) {
			endpoint = "http://localhost:9000";
		}
		if (bucket == null || bucket.isBlank()) {
			bucket = "resumes";
		}
		if (encryption == null || encryption.isBlank()) {
			encryption = "none";
		}
		if (signedUrlTtl == null) {
			signedUrlTtl = Duration.ofMinutes(15);
		}
	}

	/** Server-side encryption at rest (SSE-S3) keeps transparent signed-URL access (DPDP §C). */
	public boolean sseEnabled() {
		return "sse".equalsIgnoreCase(encryption);
	}
}
