package com.naukrinearby.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.naukrinearby.config.SpeechProperties;
import com.naukrinearby.config.TranslationProperties;
import com.naukrinearby.exception.RateLimitException;
import com.naukrinearby.exception.ServiceUnavailableException;
import com.naukrinearby.exception.ValidationException;
import com.naukrinearby.generation.SpeechToTextProvider;
import com.naukrinearby.generation.TextToSpeechProvider;
import com.naukrinearby.generation.TranslationProvider;
import com.naukrinearby.model.dto.speech.JobAudio;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.util.RedisRateLimiter;
import com.naukrinearby.util.WavFixtures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Validation, caching, translation and fallback rules for speech (no Spring context). */
class SpeechServiceTest {

	private final Map<String, String> redisStore = new HashMap<>();
	private final AtomicInteger ttsCalls = new AtomicInteger();
	private final Map<String, String> spoken = new HashMap<>();
	private boolean ttsFails;
	private boolean translationFails;
	private boolean withinLimit = true;
	private Job job;
	private SpeechService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		job = new Job();
		job.setId(42L);
		job.setVersion(3);
		job.setTitle("Delivery Partner");
		job.setDescription("Deliver groceries on your own bike.");

		JobService jobService = mock(JobService.class);
		when(jobService.getPublicJob("delivery-partner")).thenReturn(job);

		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		when(ops.get(anyString())).thenAnswer(inv -> redisStore.get(inv.<String>getArgument(0)));
		doAnswer(inv -> redisStore.put(inv.getArgument(0), inv.getArgument(1)))
				.when(ops).set(anyString(), anyString(), any(Duration.class));

		RedisRateLimiter limiter = mock(RedisRateLimiter.class);
		when(limiter.tryAcquire(anyString(), anyLong(), any(Duration.class))).thenAnswer(inv -> withinLimit);

		TranslationProvider translator = new TranslationProvider() {
			@Override
			public String translate(String text, String source, String target) {
				if (translationFails) {
					throw new IllegalStateException("Bhashini down");
				}
				return "[" + target + "] " + text;
			}

			@Override
			public String providerId() {
				return "fake";
			}
		};
		JobTranslationService jobTranslation = new JobTranslationService(jobService, new TranslationService(translator),
				redis, new JsonMapper(), new TranslationProperties("bhashini", null, "u", "k", "p", null, null, null));

		TextToSpeechProvider tts = new TextToSpeechProvider() {
			@Override
			public byte[] synthesize(String text, String languageCode) {
				ttsCalls.incrementAndGet();
				if (ttsFails) {
					throw new IllegalStateException("Bhashini down");
				}
				spoken.put(languageCode, text);
				return WavFixtures.silence(22_050, 1, 16, 0.01);
			}

			@Override
			public String providerId() {
				return "fake";
			}
		};
		SpeechToTextProvider asr = new SpeechToTextProvider() {
			@Override
			public String transcribe(byte[] wav, String languageCode) {
				if (ttsFails) {
					throw new IllegalStateException("Bhashini down");
				}
				return "  delivery job ";
			}

			@Override
			public String providerId() {
				return "fake";
			}
		};
		service = new SpeechService(asr, tts, jobService, jobTranslation, limiter, redis,
				new SpeechProperties(null, null, null, null, null, null, null, null, null, null, null, null, null,
						null, null));
	}

	@Test
	void jobAudio_translatesFirst_andIsCachedByJobVersionAndLanguage() {
		JobAudio first = service.jobAudio("delivery-partner", "hi", "ip:1");
		assertThat(first.language()).isEqualTo("hi");
		assertThat(first.isEmpty()).isFalse();
		assertThat(spoken.get("hi")).isEqualTo("[hi] Delivery Partner. [hi] Deliver groceries on your own bike.");

		service.jobAudio("delivery-partner", "hi", "ip:1");
		assertThat(ttsCalls).hasValue(1);
		assertThat(redisStore).containsKey("jobaudio:42:v3:hi");

		job.setVersion(4); // an edit invalidates the audio
		service.jobAudio("delivery-partner", "hi", "ip:1");
		assertThat(ttsCalls).hasValue(2);
	}

	@Test
	void jobAudio_inTheSourceLanguage_skipsTranslation() {
		JobAudio audio = service.jobAudio("delivery-partner", "en", "ip:1");
		assertThat(audio.language()).isEqualTo("en");
		assertThat(spoken.get("en")).isEqualTo("Delivery Partner. Deliver groceries on your own bike.");
	}

	@Test
	void jobAudio_whenTranslationFails_speaksTheOriginalLanguage() {
		translationFails = true;
		JobAudio audio = service.jobAudio("delivery-partner", "hi", "ip:1");
		assertThat(audio.language()).isEqualTo("en");
		assertThat(spoken).containsOnlyKeys("en");
	}

	@Test
	void jobAudio_providerFailure_is503_andNotCached() {
		ttsFails = true;
		assertThatThrownBy(() -> service.jobAudio("delivery-partner", "hi", "ip:1"))
				.isInstanceOf(ServiceUnavailableException.class);
		assertThat(redisStore.keySet()).noneMatch(k -> k.startsWith("jobaudio:"));
	}

	@Test
	void jobAudio_cacheMisses_areRateLimited() {
		withinLimit = false;
		assertThatThrownBy(() -> service.jobAudio("delivery-partner", "hi", "ip:1"))
				.isInstanceOf(RateLimitException.class);
		assertThat(ttsCalls).hasValue(0);
	}

	@Test
	void transcribe_validatesTheClip_andTrimsTheTranscript() {
		assertThat(service.transcribe(WavFixtures.voiceSearchClip(1), "hi", "ip:1").text()).isEqualTo("delivery job");

		assertThatThrownBy(() -> service.transcribe(WavFixtures.voiceSearchClip(1), "xx", "ip:1"))
				.isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> service.transcribe("hello".getBytes(), "hi", "ip:1"))
				.isInstanceOf(ValidationException.class).hasMessageContaining("WAV");
		assertThatThrownBy(() -> service.transcribe(WavFixtures.silence(44_100, 1, 16, 1), "hi", "ip:1"))
				.isInstanceOf(ValidationException.class).hasMessageContaining("16 kHz");
		// 33 s at 16 kHz is over 1 MB, so the size cap rejects it before the header is read.
		assertThatThrownBy(() -> service.transcribe(WavFixtures.voiceSearchClip(33), "hi", "ip:1"))
				.isInstanceOf(ValidationException.class).hasMessageContaining("bytes");
	}

	@Test
	void transcribe_durationCap_appliesBelowTheSizeCap() {
		service = new SpeechService(new com.naukrinearby.generation.StubSpeechProvider(),
				new com.naukrinearby.generation.StubSpeechProvider(), null, null, rateLimiterAllowingAll(), null,
				new SpeechProperties(null, null, null, null, null, null, null, null, null, 5, null, null, null, null,
						null));
		assertThat(service.transcribe(WavFixtures.voiceSearchClip(5), "hi", "ip:1").text()).isEmpty();
		assertThatThrownBy(() -> service.transcribe(WavFixtures.voiceSearchClip(6), "hi", "ip:1"))
				.isInstanceOf(ValidationException.class).hasMessageContaining("seconds");
	}

	@Test
	void transcribe_providerFailure_is503_andOverLimitIs429() {
		ttsFails = true;
		assertThatThrownBy(() -> service.transcribe(WavFixtures.voiceSearchClip(1), "hi", "ip:1"))
				.isInstanceOf(ServiceUnavailableException.class);
		withinLimit = false;
		assertThatThrownBy(() -> service.transcribe(WavFixtures.voiceSearchClip(1), "hi", "ip:1"))
				.isInstanceOf(RateLimitException.class);
	}

	@Test
	void spokenText_cutsTheDescriptionAtASentence() {
		assertThat(SpeechService.spokenText("Cook", "Make rotis. Clean the kitchen. Shop for vegetables.", 40))
				.isEqualTo("Cook. Make rotis. Clean the kitchen.");
		int max = "रसोइया".length() + "रोटी बनाना। रसोई साफ़ करना। सब".length();
		assertThat(SpeechService.spokenText("रसोइया", "रोटी बनाना। रसोई साफ़ करना। सब्ज़ी लाना।", max))
				.isEqualTo("रसोइया. रोटी बनाना। रसोई साफ़ करना।");
		assertThat(SpeechService.spokenText("Cook", null, 30)).isEqualTo("Cook");
	}

	private static RedisRateLimiter rateLimiterAllowingAll() {
		RedisRateLimiter limiter = mock(RedisRateLimiter.class);
		when(limiter.tryAcquire(anyString(), anyLong(), any(Duration.class))).thenReturn(true);
		return limiter;
	}
}
