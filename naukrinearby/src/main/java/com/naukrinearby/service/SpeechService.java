package com.naukrinearby.service;

import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

import com.naukrinearby.config.SpeechProperties;
import com.naukrinearby.exception.RateLimitException;
import com.naukrinearby.exception.ServiceUnavailableException;
import com.naukrinearby.exception.ValidationException;
import com.naukrinearby.generation.SpeechToTextProvider;
import com.naukrinearby.generation.TextToSpeechProvider;
import com.naukrinearby.model.dto.job.JobTranslationResponse;
import com.naukrinearby.model.dto.speech.JobAudio;
import com.naukrinearby.model.dto.speech.TranscriptionResponse;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.util.RedisRateLimiter;
import com.naukrinearby.util.WavFormat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Bhashini speech for low-literacy and voice-first users (ADR 0009).
 * <ul>
 * <li><b>Transcribe</b> — the voice-search fallback where the browser lacks the Web Speech API. Accepts
 * only 16 kHz mono 16-bit PCM WAV within the size and duration caps, rate-limited per user/IP.</li>
 * <li><b>Job audio</b> — the "Listen" button. Speaks the title and the start of the description, translated
 * first through {@link JobTranslationService} when the reader's language differs. Audio is cached in Redis
 * under job id + version + spoken language, so an edit invalidates it and a popular job costs one provider
 * call per language. Cache misses are rate-limited per user/IP.</li>
 * </ul>
 * Provider failures surface as 503 so the UI can fall back (typing, or the browser's own voices). Audio,
 * transcripts and spoken text are never logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpeechService {

	private final SpeechToTextProvider speechToText;
	private final TextToSpeechProvider textToSpeech;
	private final JobService jobService;
	private final JobTranslationService jobTranslationService;
	private final RedisRateLimiter rateLimiter;
	private final StringRedisTemplate redis;
	private final SpeechProperties props;

	public TranscriptionResponse transcribe(byte[] audio, String languageCode, String callerId) {
		String lang = JobTranslationService.normalizeLanguage(languageCode);
		if (audio == null || audio.length == 0) {
			throw new ValidationException("audio is required");
		}
		if (audio.length > props.maxAudioBytes()) {
			throw new ValidationException("audio is larger than " + props.maxAudioBytes() + " bytes");
		}
		WavFormat wav = WavFormat.parse(audio)
				.orElseThrow(() -> new ValidationException("audio must be a WAV file"));
		if (!wav.isPcm16Mono(16_000)) {
			throw new ValidationException("audio must be 16 kHz mono 16-bit PCM WAV");
		}
		if (wav.durationSeconds() > props.maxAudioSeconds()) {
			throw new ValidationException("audio is longer than " + props.maxAudioSeconds() + " seconds");
		}
		if (!rateLimiter.tryAcquire("rl:speech:asr:" + callerId, props.transcribePerMinute(), Duration.ofMinutes(1))) {
			throw new RateLimitException("Too many voice searches. Try again in a minute.");
		}
		String text;
		try {
			text = speechToText.transcribe(audio, lang);
		}
		catch (Exception ex) {
			log.warn("Speech recognition ({}) failed: {}", lang, ex.getClass().getSimpleName());
			throw new ServiceUnavailableException("Voice search is unavailable right now. Please type instead.");
		}
		return new TranscriptionResponse(text == null ? "" : text.strip(), lang, speechToText.providerId());
	}

	public JobAudio jobAudio(String slug, String languageCode, String callerId) {
		String lang = JobTranslationService.normalizeLanguage(languageCode);
		Job job = jobService.getPublicJob(slug);
		JobTranslationResponse text = jobTranslationService.translate(job, lang);
		String spoken = text.translated() ? text.language() : text.sourceLanguage();
		String provider = textToSpeech.providerId();

		String key = "jobaudio:" + job.getId() + ":v" + job.getVersion() + ":" + spoken;
		Optional<byte[]> cached = readCache(key);
		if (cached.isPresent()) {
			return new JobAudio(cached.get(), spoken, provider);
		}
		if (!rateLimiter.tryAcquire("rl:speech:tts:" + callerId, props.synthesizePerMinute(), Duration.ofMinutes(1))) {
			throw new RateLimitException("Too many audio requests. Try again in a minute.");
		}
		byte[] wav;
		try {
			wav = textToSpeech.synthesize(spokenText(text.title(), text.description(), props.maxSpokenChars()), spoken);
		}
		catch (Exception ex) {
			log.warn("Speech synthesis ({}) for job {} failed: {}", spoken, job.getId(), ex.getClass().getSimpleName());
			throw new ServiceUnavailableException("Audio is unavailable right now.");
		}
		JobAudio audio = new JobAudio(wav, spoken, provider);
		if (!audio.isEmpty() && wav.length <= props.maxCachedAudioBytes()) {
			writeCache(key, wav);
		}
		return audio;
	}

	/** Title, then the description cut at the last sentence end before {@code maxChars}. */
	static String spokenText(String title, String description, int maxChars) {
		String t = title == null ? "" : title.strip();
		if (description == null || description.isBlank()) {
			return t;
		}
		String d = description.strip().replaceAll("\s+", " ");
		int budget = Math.max(0, maxChars - t.length());
		if (d.length() > budget) {
			String cut = d.substring(0, budget);
			int end = Math.max(Math.max(cut.lastIndexOf(". "), cut.lastIndexOf("। ")),
					Math.max(cut.lastIndexOf("! "), cut.lastIndexOf("? ")));
			d = end > 0 ? cut.substring(0, end + 1) : cut;
		}
		return t.isEmpty() ? d : t + ". " + d;
	}

	private Optional<byte[]> readCache(String key) {
		try {
			String b64 = redis.opsForValue().get(key);
			return b64 == null ? Optional.empty() : Optional.of(Base64.getDecoder().decode(b64));
		}
		catch (RuntimeException ex) {
			log.debug("Job audio cache read failed: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	private void writeCache(String key, byte[] wav) {
		try {
			redis.opsForValue().set(key, Base64.getEncoder().encodeToString(wav), props.audioCacheTtl());
		}
		catch (RuntimeException ex) {
			log.debug("Job audio cache write failed: {}", ex.getMessage());
		}
	}
}
