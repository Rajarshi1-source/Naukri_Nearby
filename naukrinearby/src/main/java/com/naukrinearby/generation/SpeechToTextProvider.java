package com.naukrinearby.generation;

/**
 * Provider-agnostic speech recognition (ADR 0009): the voice-search fallback for browsers without the
 * Web Speech API. The default is a stub that hears nothing; Bhashini ASR is the live provider.
 */
public interface SpeechToTextProvider {

	/**
	 * Transcribes a 16 kHz mono 16-bit PCM WAV clip spoken in {@code languageCode} (ISO-639-1, e.g.
	 * {@code hi}). Returns an empty string when nothing was recognised.
	 */
	String transcribe(byte[] wav, String languageCode);

	String providerId();
}
