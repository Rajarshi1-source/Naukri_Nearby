package com.naukrinearby.generation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Passthrough translation stub (returns English unchanged). Selected by
 * {@code naukri.translation.provider=stub} (default). A real Bhashini impl can replace it.
 */
@Component
@ConditionalOnProperty(name = "naukri.translation.provider", havingValue = "stub", matchIfMissing = true)
public class StubTranslationProvider implements TranslationProvider {

	@Override
	public String translate(String text, String targetLanguageCode) {
		return text;
	}

	@Override
	public String providerId() {
		return "stub";
	}
}
