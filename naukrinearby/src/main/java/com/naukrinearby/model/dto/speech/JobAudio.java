package com.naukrinearby.model.dto.speech;

/**
 * Spoken job text as WAV. {@code language} is what is actually spoken: the source language when the
 * translation was unavailable. Empty {@code wav} means the provider has no audio (the stub).
 */
public record JobAudio(byte[] wav, String language, String provider) {

	public boolean isEmpty() {
		return wav == null || wav.length == 0;
	}
}
