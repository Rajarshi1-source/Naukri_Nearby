package com.naukrinearby.generation;

/** Provider-agnostic translation (master plan §2). MVP ships a passthrough stub for Bhashini. */
public interface TranslationProvider {

	String translate(String text, String targetLanguageCode);

	String providerId();
}
