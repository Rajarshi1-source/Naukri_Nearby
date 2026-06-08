package com.naukrinearby.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naukri.search")
public record SearchProperties(String index, Duration cacheTtl, int maxResults) {

	public SearchProperties {
		if (index == null || index.isBlank()) {
			index = "jobs";
		}
		if (cacheTtl == null) {
			cacheTtl = Duration.ofMinutes(5);
		}
		if (maxResults <= 0) {
			maxResults = 20;
		}
	}
}
