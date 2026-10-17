package com.naukrinearby.service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.naukrinearby.config.TranslationProperties;
import com.naukrinearby.exception.ValidationException;
import com.naukrinearby.generation.TranslationProvider;
import com.naukrinearby.model.dto.job.JobTranslationResponse;
import com.naukrinearby.model.entity.Job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Caching, fallback and language rules for job translation (no Spring context). */
class JobTranslationServiceTest {

	private final Map<String, String> redisStore = new HashMap<>();
	private final AtomicInteger providerCalls = new AtomicInteger();
	private boolean providerFails;
	private Job job;
	private JobTranslationService service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		job = new Job();
		job.setId(42L);
		job.setVersion(3);
		job.setTitle("Delivery Partner");
		job.setDescription("Deliver groceries on your own bike.");

		JobService jobService = mock(JobService.class);
		when(jobService.getForViewer("delivery-partner", null, false)).thenReturn(job);

		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		when(ops.get(anyString())).thenAnswer(inv -> redisStore.get(inv.<String>getArgument(0)));
		doAnswer(inv -> redisStore.put(inv.getArgument(0), inv.getArgument(1)))
				.when(ops).set(anyString(), anyString(), any(Duration.class));

		TranslationProvider provider = new TranslationProvider() {
			@Override
			public String translate(String text, String source, String target) {
				providerCalls.incrementAndGet();
				if (providerFails) {
					throw new IllegalStateException("Bhashini down");
				}
				return "[" + target + "] " + text;
			}

			@Override
			public String providerId() {
				return "fake";
			}
		};
		service = new JobTranslationService(jobService, new TranslationService(provider), redis, new JsonMapper(),
				new TranslationProperties("bhashini", null, "u", "k", "p", null, null, null));
	}

	@Test
	void translatesTitleAndDescription_andCachesByJobVersion() {
		JobTranslationResponse first = service.translate("delivery-partner", "hi");
		assertThat(first.translated()).isTrue();
		assertThat(first.title()).isEqualTo("[hi] Delivery Partner");
		assertThat(first.description()).isEqualTo("[hi] Deliver groceries on your own bike.");
		assertThat(redisStore).containsKey("jobtr:42:v3:hi");

		service.translate("delivery-partner", "hi");
		assertThat(providerCalls).hasValue(2); // second read came from the cache

		job.setVersion(4); // an edit invalidates the cached translation
		service.translate("delivery-partner", "hi");
		assertThat(providerCalls).hasValue(4);
	}

	@Test
	void providerFailure_returnsTheOriginal_andIsNotCached() {
		providerFails = true;
		JobTranslationResponse r = service.translate("delivery-partner", "ta");
		assertThat(r.translated()).isFalse();
		assertThat(r.title()).isEqualTo("Delivery Partner");
		assertThat(redisStore).isEmpty();
	}

	@Test
	void hindiJob_isNotTranslatedToHindi() {
		job.setTitle("डिलीवरी पार्टनर");
		JobTranslationResponse r = service.translate("delivery-partner", "hi");
		assertThat(r.translated()).isFalse();
		assertThat(r.sourceLanguage()).isEqualTo("hi");
		assertThat(providerCalls).hasValue(0);
	}

	@Test
	void unsupportedLanguage_isRejected() {
		assertThatThrownBy(() -> service.translate("delivery-partner", "xx")).isInstanceOf(ValidationException.class);
		assertThatThrownBy(() -> service.translate("delivery-partner", " ")).isInstanceOf(ValidationException.class);
	}
}
