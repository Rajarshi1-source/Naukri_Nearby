package com.naukrinearby.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naukri.search")
public record SearchProperties(
		String index,
		Duration cacheTtl,
		int maxResults,
		String ranking,
		int rrfWindow,
		int rrfK) {

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
		if (ranking == null || ranking.isBlank()) {
			ranking = "geo";
		}
		if (rrfWindow <= 0) {
			rrfWindow = 50;
		}
		if (rrfK <= 0) {
			rrfK = 60;
		}
	}

	/** True when hybrid BM25 + pgvector Reciprocal Rank Fusion is enabled (master plan G4). */
	public boolean hybridEnabled() {
		return "hybrid".equalsIgnoreCase(ranking);
	}
}
