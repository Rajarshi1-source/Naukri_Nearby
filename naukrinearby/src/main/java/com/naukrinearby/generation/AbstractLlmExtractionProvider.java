package com.naukrinearby.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.naukrinearby.exception.ResumeParseException;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class doing the JSON-mode call contract, defensive fence-stripping, and Jackson mapping
 * (eval connectors §6). Concrete providers implement only the raw model call (temperature 0 + JSON).
 * The public entry point is guarded by a Resilience4j retry + circuit breaker (master plan §12); on an
 * open breaker the call throws and the caller (e.g. ResumeParserService) records a FAILED parse.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractLlmExtractionProvider implements LlmExtractionProvider {

	protected final ObjectMapper objectMapper;

	/** Subclasses implement the actual API call. MUST use temperature 0 + JSON output. */
	protected abstract String callModelRaw(String prompt);

	@Override
	@Retry(name = "llm")
	@CircuitBreaker(name = "llm")
	public <T> T extractStructured(String prompt, Class<T> schema, String promptVersion) {
		long start = System.currentTimeMillis();
		String raw = callModelRaw(prompt);
		String json = stripFences(raw);
		try {
			T result = objectMapper.readValue(json, schema);
			log.info("extraction ok provider={} promptVersion={} ms={}",
					providerId(), promptVersion, System.currentTimeMillis() - start);
			return result;
		}
		catch (Exception ex) {
			log.error("extraction JSON parse failed provider={}", providerId(), ex);
			throw new ResumeParseException("Model did not return valid JSON", ex);
		}
	}

	/** Defensive: the prompt forbids fences, but strip ```json ... ``` if a model adds them. */
	protected String stripFences(String s) {
		if (s == null) {
			return "{}";
		}
		String t = s.trim();
		if (t.startsWith("```")) {
			t = t.replaceFirst("^```(json)?", "").replaceFirst("```$", "").trim();
		}
		int first = t.indexOf('{');
		int last = t.lastIndexOf('}');
		return (first >= 0 && last > first) ? t.substring(first, last + 1) : t;
	}
}
