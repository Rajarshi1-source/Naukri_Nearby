package com.naukrinearby.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * Turns on Framework 7's native {@code @ConcurrencyLimit} / {@code @Retryable}. The project uses
 * {@code @ConcurrencyLimit} as a bulkhead on the resume-parse path so a burst of uploads cannot
 * starve WhatsApp delivery or search. Retries on HTTP adapters stay on Resilience4j — never stack
 * both retry mechanisms on one method.
 */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
public class ResilienceConfig {
}
