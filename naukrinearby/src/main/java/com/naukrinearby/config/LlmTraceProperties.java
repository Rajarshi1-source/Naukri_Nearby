package com.naukrinearby.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM observability (plan D13). {@code provider} is {@code none} (default), {@code log}, or
 * {@code langfuse}. Langfuse keys come from the environment; traces carry metadata only, never
 * resume text or phone numbers.
 */
@ConfigurationProperties(prefix = "naukri.llm-trace")
public record LlmTraceProperties(String provider, String host, String publicKey, String secretKey, Duration timeout) {

	public LlmTraceProperties {
		if (provider == null || provider.isBlank()) {
			provider = "none";
		}
		if (timeout == null) {
			timeout = Duration.ofSeconds(5);
		}
	}
}
