package com.naukrinearby.service;

import com.naukrinearby.generation.TranslationProvider;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Translation with graceful degradation (master plan §9, mitigation §13): if the provider fails or
 * times out, we fall back to the original English text rather than dropping the alert. (The skill's
 * Resilience4j {@code @CircuitBreaker} maps onto this try/fallback; native circuit breakers aren't
 * yet packaged for Spring Boot 4, so the fallback is implemented explicitly — see AGENTS.md.)
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
