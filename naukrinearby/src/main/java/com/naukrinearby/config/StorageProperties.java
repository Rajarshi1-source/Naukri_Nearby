package com.naukrinearby.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naukri.storage")
public record StorageProperties(
		String endpoint,
		String accessKey,
		String secretKey,
		String bucket,
		Duration signedUrlTtl) {

	public StorageProperties {
		if (endpoint == null || endpoint.isBlank()) {
			endpoint = "http://localhost:9000";
		}
		if (bucket == null || bucket.isBlank()) {
			bucket = "resumes";
		}
		if (signedUrlTtl == null) {
			signedUrlTtl = Duration.ofMinutes(15);
		}
	}
}
