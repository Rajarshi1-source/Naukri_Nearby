package com.naukrinearby.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naukri.llm")
public record LlmProperties(
		String extractionProvider,
		String extractionModel,
		String visionModel,
		String baseUrl,
		String apiKey,
		double temperature,
		Duration timeout) {

	public LlmProperties {
		if (extractionProvider == null || extractionProvider.isBlank()) {
			extractionProvider = "gpt5mini";
		}
		if (extractionModel == null || extractionModel.isBlank()) {
			extractionModel = "gpt-5-mini";
		}
		if (baseUrl == null || baseUrl.isBlank()) {
			baseUrl = "https://api.openai.com/v1";
		}
		if (timeout == null) {
			timeout = Duration.ofSeconds(60);
		}
	}
}
