package com.naukrinearby.controller;

import java.io.IOException;
import java.time.Duration;

import com.naukrinearby.exception.ValidationException;
import com.naukrinearby.model.dto.speech.JobAudio;
import com.naukrinearby.model.dto.speech.TranscriptionResponse;
import com.naukrinearby.security.ApiRateLimitFilter;
import com.naukrinearby.service.SpeechService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Bhashini speech (ADR 0009): the voice-search fallback and the job "Listen" audio. */
@RestController
@RequiredArgsConstructor
public class SpeechController {

	static final MediaType AUDIO_WAV = MediaType.parseMediaType("audio/wav");

	private final SpeechService speechService;

	/** Multipart {@code audio} (16 kHz mono WAV, up to about 30 s / 1 MB) and {@code lang}; returns the transcript. */
	@PostMapping(path = "/api/speech/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public TranscriptionResponse transcribe(@RequestPart(name = "audio", required = false) MultipartFile audio,
			@RequestParam(required = false) String lang, HttpServletRequest request) throws IOException {
		if (audio == null) {
			throw new ValidationException("audio is required");
		}
		return speechService.transcribe(audio.getBytes(), lang, ApiRateLimitFilter.callerId(request));
	}

	/**
	 * A public job's title and description spoken in {@code lang} as WAV. {@code Content-Language} names
	 * what is actually spoken (the original language if translation was unavailable); 204 means the
	 * provider has no audio, so the UI should not offer playback.
	 */
	@GetMapping("/api/jobs/{slug}/audio")
	public ResponseEntity<byte[]> jobAudio(@PathVariable String slug, @RequestParam(required = false) String lang,
			HttpServletRequest request) {
		JobAudio audio = speechService.jobAudio(slug, lang, ApiRateLimitFilter.callerId(request));
		if (audio.isEmpty()) {
			return ResponseEntity.noContent().header("X-Speech-Provider", audio.provider()).build();
		}
		return ResponseEntity.ok()
				.contentType(AUDIO_WAV)
				.header(HttpHeaders.CONTENT_LANGUAGE, audio.language())
				.header("X-Speech-Provider", audio.provider())
				.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
				.body(audio.wav());
	}
}
