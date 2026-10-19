package com.naukrinearby.generation;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.naukrinearby.config.TranslationProperties;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Bhashini (Government of India's language stack for 22 Indian languages) via its ULCA pipeline API,
 * task type {@code translation}. The config + compute exchange lives in {@link BhashiniPipelineClient};
 * the compute body is {@code inputData.input[{source}]} and the result is
 * {@code pipelineResponse[0].output[0].target}.
 * Retries and the circuit breaker wrap the whole exchange; {@code TranslationService} falls back to the
 * original text if it still fails. Selected by {@code naukri.translation.provider=bhashini}.
 */
@Component
@ConditionalOnProperty(name = "naukri.translation.provider", havingValue = "bhashini")
public class BhashiniTranslationProvider implements TranslationProvider {

	private final BhashiniPipelineClient pipeline;

	@Autowired
	public BhashiniTranslationProvider(TranslationProperties props) {
		this(props, Clock.systemUTC());
	}

	BhashiniTranslationProvider(TranslationProperties props, Clock clock) {
		if (BhashiniPipelineClient.isBlank(props.userId()) || BhashiniPipelineClient.isBlank(props.ulcaApiKey())
				|| BhashiniPipelineClient.isBlank(props.pipelineId())) {
			throw new IllegalStateException("naukri.translation.provider=bhashini needs BHASHINI_USER_ID, "
					+ "BHASHINI_ULCA_API_KEY and BHASHINI_PIPELINE_ID");
		}
		this.pipeline = new BhashiniPipelineClient(props.configUrl(), props.userId(), props.ulcaApiKey(),
				props.pipelineId(), props.timeout(), props.configTtl(), clock);
	}

	@Override
	@Retry(name = "bhashini")
	@CircuitBreaker(name = "bhashini")
	public String translate(String text, String sourceLanguageCode, String targetLanguageCode) {
		if (text == null || text.isBlank() || sourceLanguageCode.equals(targetLanguageCode)) {
			return text;
		}
		var response = pipeline.compute("translation",
				Map.of("sourceLanguage", sourceLanguageCode, "targetLanguage", targetLanguageCode),
				Map.of(),
				Map.of("input", List.of(Map.of("source", text))));
		var output = response.first().firstOutput();
		String target = output == null ? null : output.target();
		if (target == null || target.isBlank()) {
			throw new IllegalStateException("Bhashini returned no translation");
		}
		return target;
	}

	@Override
	public String providerId() {
		return "bhashini";
	}
}
