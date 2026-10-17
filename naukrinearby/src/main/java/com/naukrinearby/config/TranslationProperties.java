package com.naukrinearby.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Translation provider config (master plan §2, ADR 0002). Default provider is {@code stub}
 * (passthrough). {@code bhashini} calls the Bhashini ULCA pipeline API: a config call to
 * {@code configUrl} with {@code userId}/{@code ulcaApiKey} and {@code pipelineId} returns the
 * serviceId and the compute endpoint plus its inference key, which are cached for {@code configTtl}.
 *
 * @param jobCacheTtl how long a translated job title/description stays in Redis
 */
@ConfigurationProperties(prefix = "naukri.translation")
public record TranslationProperties(
		String provider,
		String configUrl,
		String userId,
		String ulcaApiKey,
		String pipelineId,
		Duration timeout,
		Duration configTtl,
		Duration jobCacheTtl) {

	public static final String DEFAULT_CONFIG_URL =
			"https://meity-auth.ulcacontrib.org/ulca/apis/v0/model/getModelsPipeline";

	public TranslationProperties {
		if (provider == null || provider.isBlank()) {
			provider = "stub";
		}
		if (configUrl == null || configUrl.isBlank()) {
			configUrl = DEFAULT_CONFIG_URL;
		}
		if (timeout == null) {
			timeout = Duration.ofSeconds(10);
		}
		if (configTtl == null) {
			configTtl = Duration.ofHours(1);
		}
		if (jobCacheTtl == null) {
			jobCacheTtl = Duration.ofDays(7);
		}
	}
}
