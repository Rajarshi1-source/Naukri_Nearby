package com.naukrinearby.generation;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.naukrinearby.config.TranslationProperties;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real translation provider for a Bhashini-compatible HTTP endpoint (Government of India's
 * translation stack for 22 Indian languages). Selected by {@code naukri.translation.provider=bhashini};
 * the {@link StubTranslationProvider} (passthrough) stays the default. Posts
 * {@code {sourceLanguage:"en", targetLanguage:<code>, text:<text>}} and reads {@code translatedText}.
 * Source is always English (job-alert templates are composed in English first — master plan §7.6).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "naukri.translation.provider", havingValue = "bhashini")
public class BhashiniTranslationProvider implements TranslationProvider {

	private final RestClient client;

	public BhashiniTranslationProvider(TranslationProperties props) {
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout((int) Math.min(Integer.MAX_VALUE, props.timeout().toMillis()));
		factory.setReadTimeout((int) Math.min(Integer.MAX_VALUE, props.timeout().toMillis()));
		var builder = RestClient.builder()
				.baseUrl(props.baseUrl() == null ? "" : props.baseUrl());
		if (props.apiKey() != null && !props.apiKey().isBlank()) {
			builder.defaultHeader("Authorization", props.apiKey());
		}
		this.client = builder.requestFactory(factory).build();
	}

	@Override
	@Retry(name = "bhashini")
	@CircuitBreaker(name = "bhashini")
	public String translate(String text, String targetLanguageCode) {
		Map<String, Object> body = Map.of(
				"sourceLanguage", "en",
				"targetLanguage", targetLanguageCode,
				"text", text);
		TranslateResponse response = client.post()
				.uri("/translate")
				.body(body)
				.retrieve()
				.body(TranslateResponse.class);
		if (response == null || response.translatedText() == null || response.translatedText().isBlank()) {
			throw new IllegalStateException("Empty translation response");
		}
		return response.translatedText();
	}

	@Override
	public String providerId() {
		return "bhashini";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record TranslateResponse(String translatedText) {
	}
}
