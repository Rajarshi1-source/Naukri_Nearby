package com.naukrinearby.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naukri.eval")
public record EvalProperties(boolean enabled, String apiKey, boolean traceEnabled) {
}
