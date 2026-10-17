package com.naukrinearby.service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.naukrinearby.config.TranslationProperties;
import com.naukrinearby.exception.ValidationException;
import com.naukrinearby.model.dto.job.JobTranslationResponse;
import com.naukrinearby.model.entity.Job;
import com.naukrinearby.model.enums.Language;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Job title and description in the reader's language (Bhashini through {@link TranslationService}).
 * Translations are made on first request and cached in Redis under the job's version, so an edit
 * naturally invalidates them and a popular job costs one provider call per language, not one per view.
 * Only successful translations are cached; a failure returns the original with {@code translated=false}
 * and is retried on the next request. Only publicly visible jobs can be translated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobTranslationService {

	/** Long descriptions are cut before translation to bound provider cost and latency. */
	static final int MAX_DESCRIPTION_CHARS = 4000;
	private static final Pattern DEVANAGARI = Pattern.compile("[\\u0900-\\u097F]");

	private final JobService jobService;
	private final TranslationService translationService;
	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	private final TranslationProperties props;

	public JobTranslationResponse translate(String slug, String languageCode) {
		String target = normalizeLanguage(languageCode);
		Job job = jobService.getForViewer(slug, null, false);
		String source = DEVANAGARI.matcher(job.getTitle() + " " + nz(job.getDescription())).find() ? "hi" : "en";
		String description = truncate(job.getDescription());
		String provider = translationService.providerId();
		if (target.equals(source)) {
			return new JobTranslationResponse(job.getId(), target, source, job.getTitle(), description, false, provider);
		}

		String key = "jobtr:" + job.getId() + ":v" + job.getVersion() + ":" + target;
		Optional<Cached> cached = readCache(key);
		if (cached.isPresent()) {
			return new JobTranslationResponse(job.getId(), target, source, cached.get().title(),
					cached.get().description(), true, provider);
		}

		Optional<String> title = translationService.translate(job.getTitle(), source, target);
		Optional<String> desc = description == null ? Optional.empty()
				: translationService.translate(description, source, target);
		boolean complete = title.isPresent() && (description == null || desc.isPresent());
		// The stub provider "translates" by echoing the input; that is not a translation.
		boolean translated = complete && !(title.get().equals(job.getTitle())
				&& (description == null || desc.get().equals(description)));
		if (!translated) {
			return new JobTranslationResponse(job.getId(), target, source, job.getTitle(), description, false, provider);
		}
		Cached value = new Cached(title.get(), desc.orElse(null));
		writeCache(key, value);
		return new JobTranslationResponse(job.getId(), target, source, value.title(), value.description(), true, provider);
	}

	private Optional<Cached> readCache(String key) {
		try {
			String json = redis.opsForValue().get(key);
			return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, Cached.class));
		}
		catch (RuntimeException ex) {
			log.debug("Job translation cache read failed: {}", ex.getMessage());
			return Optional.empty();
		}
	}

	private void writeCache(String key, Cached value) {
		try {
			redis.opsForValue().set(key, objectMapper.writeValueAsString(value), props.jobCacheTtl());
		}
		catch (RuntimeException ex) {
			log.debug("Job translation cache write failed: {}", ex.getMessage());
		}
	}

	private static String normalizeLanguage(String code) {
		if (code == null || code.isBlank()) {
			throw new ValidationException("lang is required");
		}
		String c = code.strip().toLowerCase(Locale.ROOT);
		for (Language l : Language.values()) {
			if (l.getCode().equals(c)) {
				return c;
			}
		}
		throw new ValidationException("Unsupported language: " + code);
	}

	private static String truncate(String s) {
		if (s == null || s.isBlank()) {
			return null;
		}
		return s.length() > MAX_DESCRIPTION_CHARS ? s.substring(0, MAX_DESCRIPTION_CHARS) + "…" : s;
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}

	record Cached(String title, String description) {
	}
}
