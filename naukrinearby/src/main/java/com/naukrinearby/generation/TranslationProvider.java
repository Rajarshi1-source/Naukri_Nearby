package com.naukrinearby.generation;

/** Provider-agnostic translation (master plan §2). The default is a passthrough stub; Bhashini is the live provider. */
public interface TranslationProvider {

	/** English to {@code targetLanguageCode} (ISO-639-1, e.g. {@code hi}). */
	default String translate(String text, String targetLanguageCode) {
		return translate(text, "en", targetLanguageCode);
	}

	String translate(String text, String sourceLanguageCode, String targetLanguageCode);

	String providerId();
}
