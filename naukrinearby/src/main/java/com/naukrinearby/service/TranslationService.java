package com.naukrinearby.service;

import java.util.Optional;

import com.naukrinearby.generation.TranslationProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Translation with graceful degradation (master plan §9, mitigation §13): if the provider fails,
 * times out, or its Resilience4j circuit breaker is open, we fall back to the original English text
 * rather than dropping the alert. The real {@code BhashiniTranslationProvider} carries the
 * {@code @CircuitBreaker(name="bhashini")} + {@code @Retry}; this outer try/catch is the final net.
 *
 * <p>Always call the provider's three-argument method: it is the one carrying the resilience
 * annotations, and a call through the two-argument default method would skip the proxy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

	private final TranslationProvider provider;

	public String translateOrEnglish(String englishText, String targetLanguageCode) {
		return translate(englishText, "en", targetLanguageCode).orElse(englishText);
	}

	/**
	 * Translates, or returns empty when there is nothing to do (same language, blank text) or the
	 * provider failed. Callers decide what to show instead.
	 */
	public Optional<String> translate(String text, String sourceLanguageCode, String targetLanguageCode) {
		if (text == null || text.isBlank() || targetLanguageCode == null || targetLanguageCode.isBlank()
				|| targetLanguageCode.equals(sourceLanguageCode)) {
			return Optional.empty();
		}
		try {
			String translated = provider.translate(text, sourceLanguageCode, targetLanguageCode);
			return translated == null || translated.isBlank() ? Optional.empty() : Optional.of(translated);
		}
		catch (Exception ex) {
			log.warn("Translation {}->{} failed: {}", sourceLanguageCode, targetLanguageCode, ex.getMessage());
			return Optional.empty();
		}
	}

	public String providerId() {
		return provider.providerId();
	}
}
