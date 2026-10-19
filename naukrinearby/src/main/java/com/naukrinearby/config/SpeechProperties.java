package com.naukrinearby.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Speech provider config (ADR 0009): ASR for the voice-search fallback and TTS for the job "Listen"
 * button. Default provider is {@code stub} (ASR returns an empty transcript, TTS returns no audio).
 * {@code bhashini} uses the same ULCA pipeline credentials as translation (the env vars are shared in
 * {@code application.yml}); {@code pipelineId} may name a different pipeline if speech lives elsewhere.
 *
 * @param maxAudioBytes  upload cap for {@code /api/speech/transcribe} (about 30 s of 16 kHz mono WAV)
 * @param maxAudioSeconds duration cap computed from the WAV header
 * @param transcribePerMinute per user/IP limit on transcriptions (provider calls are costly)
 * @param synthesizePerMinute per user/IP limit on job audio cache misses
 * @param maxSpokenChars job text spoken by the Listen button is cut at a sentence near this length
 * @param audioCacheTtl how long synthesized job audio stays in Redis
 * @param maxCachedAudioBytes larger audio is served but not cached
 */
@ConfigurationProperties(prefix = "naukri.speech")
public record SpeechProperties(
		String provider,
		String configUrl,
		String userId,
		String ulcaApiKey,
		String pipelineId,
		Duration timeout,
		Duration configTtl,
		String ttsGender,
		Integer maxAudioBytes,
		Integer maxAudioSeconds,
		Integer transcribePerMinute,
		Integer synthesizePerMinute,
		Integer maxSpokenChars,
		Duration audioCacheTtl,
		Integer maxCachedAudioBytes) {

	public SpeechProperties {
		if (provider == null || provider.isBlank()) {
			provider = "stub";
		}
		if (configUrl == null || configUrl.isBlank()) {
			configUrl = TranslationProperties.DEFAULT_CONFIG_URL;
		}
		if (timeout == null) {
			timeout = Duration.ofSeconds(20);
		}
		if (configTtl == null) {
			configTtl = Duration.ofHours(1);
		}
		if (ttsGender == null || ttsGender.isBlank()) {
			ttsGender = "female";
		}
		if (maxAudioBytes == null) {
			maxAudioBytes = 1_048_576;
		}
		if (maxAudioSeconds == null) {
			maxAudioSeconds = 30;
		}
		if (transcribePerMinute == null) {
			transcribePerMinute = 10;
		}
		if (synthesizePerMinute == null) {
			synthesizePerMinute = 10;
		}
		if (maxSpokenChars == null) {
			maxSpokenChars = 700;
		}
		if (audioCacheTtl == null) {
			audioCacheTtl = Duration.ofDays(7);
		}
		if (maxCachedAudioBytes == null) {
			maxCachedAudioBytes = 4 * 1_048_576;
		}
	}
}
