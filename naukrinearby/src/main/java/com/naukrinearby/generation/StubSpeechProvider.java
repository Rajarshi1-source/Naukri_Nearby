package com.naukrinearby.generation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Speech stub, selected by {@code naukri.speech.provider=stub} (default): ASR hears nothing and TTS has no
 * audio, so the UI falls back to typing and to the browser's own voices.
 */
@Component
@ConditionalOnProperty(name = "naukri.speech.provider", havingValue = "stub", matchIfMissing = true)
public class StubSpeechProvider implements SpeechToTextProvider, TextToSpeechProvider {

	@Override
	public String transcribe(byte[] wav, String languageCode) {
		return "";
	}

	@Override
	public byte[] synthesize(String text, String languageCode) {
		return new byte[0];
	}

	@Override
	public String providerId() {
		return "stub";
	}
}
