package com.naukrinearby.service;

import com.naukrinearby.generation.TranslationProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Translation with graceful degradation (master plan §9, mitigation §13): if the provider fails,
 * times out, or its Resilience4j circuit breaker is open, we fall back to the original English text
 * rather than dropping the alert. The real {@code BhashiniTranslationProvider} carries the
 * {@code @CircuitBreaker(name="bhashini")} + {@code @Retry}; this outer try/catch is the final net.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationService {

	private final TranslationProvider provider;

	public String translateOrEnglish(String englishText, String targetLanguageCode) {
		if (targetLanguageCode == null || targetLanguageCode.isBlank() || targetLanguageCode.equals("en")) {
			return englishText;
		}
		try {
			String translated = provider.translate(englishText, targetLanguageCode);
			return (translated == null || translated.isBlank()) ? englishText : translated;
		}
		catch (Exception ex) {
			log.warn("Translation to '{}' failed, sending English: {}", targetLanguageCode, ex.getMessage());
			return englishText;
		}
	}
}
