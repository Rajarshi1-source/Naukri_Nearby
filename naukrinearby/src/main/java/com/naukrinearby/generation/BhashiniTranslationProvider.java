package com.naukrinearby.generation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.naukrinearby.config.TranslationProperties;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Bhashini (Government of India's language stack for 22 Indian languages) via its ULCA pipeline API.
 * Two calls, per the official docs (bhashini.gitbook.io/bhashini-apis):
 * <ol>
 * <li><b>Pipeline config</b> — {@code POST getModelsPipeline} with headers {@code userID} and
 * {@code ulcaApiKey}, body {@code pipelineTasks[{taskType:"translation", config.language}]} and
 * {@code pipelineRequestConfig.pipelineId}. The response names the {@code serviceId} for the language
 * pair and the compute endpoint ({@code pipelineInferenceAPIEndPoint.callbackUrl}) with its
 * {@code inferenceApiKey} header name and value. Cached per language pair for {@code configTtl}.</li>
 * <li><b>Pipeline compute</b> — {@code POST callbackUrl} with that header, body
 * {@code pipelineTasks[{taskType, config:{language, serviceId}}]} and
 * {@code inputData.input[{source}]}; the result is {@code pipelineResponse[0].output[0].target}.</li>
 * </ol>
 * Retries and the circuit breaker wrap the whole exchange; {@code TranslationService} falls back to the
 * original text if it still fails. Selected by {@code naukri.translation.provider=bhashini}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "naukri.translation.provider", havingValue = "bhashini")
public class BhashiniTranslationProvider implements TranslationProvider {

	private final RestClient client;
	private final TranslationProperties props;
	private final Clock clock;
	private final Map<String, PipelineConfig> configs = new ConcurrentHashMap<>();

	@Autowired
	public BhashiniTranslationProvider(TranslationProperties props) {
		this(props, Clock.systemUTC());
	}

	BhashiniTranslationProvider(TranslationProperties props, Clock clock) {
		if (isBlank(props.userId()) || isBlank(props.ulcaApiKey()) || isBlank(props.pipelineId())) {
			throw new IllegalStateException("naukri.translation.provider=bhashini needs BHASHINI_USER_ID, "
					+ "BHASHINI_ULCA_API_KEY and BHASHINI_PIPELINE_ID");
		}
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(props.timeout());
		factory.setReadTimeout(props.timeout());
		this.client = RestClient.builder().requestFactory(factory).build();
		this.props = props;
		this.clock = clock;
	}

	@Override
	@Retry(name = "bhashini")
	@CircuitBreaker(name = "bhashini")
	public String translate(String text, String sourceLanguageCode, String targetLanguageCode) {
		if (text == null || text.isBlank() || sourceLanguageCode.equals(targetLanguageCode)) {
			return text;
		}
		PipelineConfig config = config(sourceLanguageCode, targetLanguageCode);
		Map<String, Object> body = Map.of(
				"pipelineTasks", List.of(Map.of(
						"taskType", "translation",
						"config", Map.of(
								"language", Map.of("sourceLanguage", sourceLanguageCode, "targetLanguage", targetLanguageCode),
								"serviceId", config.serviceId()))),
				"inputData", Map.of("input", List.of(Map.of("source", text))));
		ComputeResponse response;
		try {
			response = client.post()
					.uri(config.endpoint())
					.contentType(MediaType.APPLICATION_JSON)
					.header(config.keyName(), config.keyValue())
					.body(body)
					.retrieve()
					.body(ComputeResponse.class);
		}
		catch (HttpClientErrorException ex) {
			if (ex.getStatusCode() == HttpStatus.UNAUTHORIZED || ex.getStatusCode() == HttpStatus.FORBIDDEN) {
				// The inference key may have been rotated: force a fresh config call on the retry.
				configs.remove(pairKey(sourceLanguageCode, targetLanguageCode));
			}
			throw ex;
		}
		String target = response == null ? null : response.firstTarget();
		if (target == null || target.isBlank()) {
			throw new IllegalStateException("Bhashini returned no translation");
		}
		return target;
	}

	private PipelineConfig config(String source, String target) {
		String key = pairKey(source, target);
		PipelineConfig cached = configs.get(key);
		if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
			return cached;
		}
		Map<String, Object> body = Map.of(
				"pipelineTasks", List.of(Map.of(
						"taskType", "translation",
						"config", Map.of("language", Map.of("sourceLanguage", source, "targetLanguage", target)))),
				"pipelineRequestConfig", Map.of("pipelineId", props.pipelineId()));
		ConfigResponse response = client.post()
				.uri(props.configUrl())
				.contentType(MediaType.APPLICATION_JSON)
				.header("userID", props.userId())
				.header("ulcaApiKey", props.ulcaApiKey())
				.body(body)
				.retrieve()
				.body(ConfigResponse.class);
		PipelineConfig fresh = PipelineConfig.from(response, source, target, clock.instant().plus(props.configTtl()));
		configs.put(key, fresh);
		log.info("Bhashini pipeline config cached for {} (serviceId {})", key, fresh.serviceId());
		return fresh;
	}

	@Override
	public String providerId() {
		return "bhashini";
	}

	private static String pairKey(String source, String target) {
		return source + "->" + target;
	}

	private static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	/** What a config call resolves for one language pair. */
	record PipelineConfig(String serviceId, String endpoint, String keyName, String keyValue, Instant expiresAt) {

		static PipelineConfig from(ConfigResponse r, String source, String target, Instant expiresAt) {
			if (r == null || r.pipelineInferenceAPIEndPoint() == null
					|| r.pipelineInferenceAPIEndPoint().callbackUrl() == null
					|| r.pipelineInferenceAPIEndPoint().inferenceApiKey() == null) {
				throw new IllegalStateException("Bhashini config response has no inference endpoint");
			}
			String serviceId = null;
			for (ConfigResponse.TaskConfig task : r.pipelineResponseConfig() == null ? List.<ConfigResponse.TaskConfig>of()
					: r.pipelineResponseConfig()) {
				if (!"translation".equals(task.taskType()) || task.config() == null) {
					continue;
				}
				for (ConfigResponse.ServiceConfig c : task.config()) {
					boolean pairMatches = c.language() != null && source.equals(c.language().sourceLanguage())
							&& target.equals(c.language().targetLanguage());
					if (serviceId == null || pairMatches) {
						serviceId = c.serviceId();
					}
					if (pairMatches) {
						break;
					}
				}
			}
			if (serviceId == null) {
				throw new IllegalStateException("Bhashini has no translation service for " + source + "->" + target);
			}
			var endpoint = r.pipelineInferenceAPIEndPoint();
			return new PipelineConfig(serviceId, endpoint.callbackUrl(), endpoint.inferenceApiKey().name(),
					endpoint.inferenceApiKey().value(), expiresAt);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ConfigResponse(List<TaskConfig> pipelineResponseConfig, Endpoint pipelineInferenceAPIEndPoint) {

		@JsonIgnoreProperties(ignoreUnknown = true)
		record TaskConfig(String taskType, List<ServiceConfig> config) {
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record ServiceConfig(String serviceId, Language language) {
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Language(String sourceLanguage, String targetLanguage) {
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Endpoint(String callbackUrl, Key inferenceApiKey) {
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Key(String name, String value) {
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ComputeResponse(List<TaskOutput> pipelineResponse) {

		String firstTarget() {
			if (pipelineResponse == null || pipelineResponse.isEmpty()) {
				return null;
			}
			List<Output> output = pipelineResponse.get(0).output();
			return output == null || output.isEmpty() ? null : output.get(0).target();
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record TaskOutput(String taskType, List<Output> output) {
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Output(String source, String target) {
		}
	}
}
