package com.naukrinearby.generation;

/**
 * Provider-agnostic speech synthesis (ADR 0009): the job page "Listen" button for low-literacy users when
 * the browser has no voice for the language. The default is a stub that returns no audio; Bhashini TTS
 * is the live provider.
 */
public interface TextToSpeechProvider {

	/** Speaks {@code text} in {@code languageCode}; returns WAV bytes, or an empty array if unsupported. */
	byte[] synthesize(String text, String languageCode);

	String providerId();
}
