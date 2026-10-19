package com.naukrinearby.generation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * The Bhashini ULCA pipeline exchange shared by the translation, ASR and TTS adapters. Two calls, per the
 * official docs (bhashini.gitbook.io/bhashini-apis):
 * <ol>
 * <li><b>Pipeline config</b> — {@code POST getModelsPipeline} with headers {@code userID} and
 * {@code ulcaApiKey}, body {@code pipelineTasks[{taskType, config.language}]} and
 * {@code pipelineRequestConfig.pipelineId}. The response names the {@code serviceId} for the language and
 * the compute endpoint ({@code pipelineInferenceAPIEndPoint.callbackUrl}) with its {@code inferenceApiKey}
 * header name and value. Cached per task type and language for {@code configTtl}.</li>
 * <li><b>Pipeline compute</b> — {@code POST callbackUrl} with that header, body
 * {@code pipelineTasks[{taskType, config:{language, serviceId, ...}}]} and {@code inputData}. A 401/403
 * drops the cached config, so the caller's retry fetches a fresh inference key.</li>
 * </ol>
 * Not a bean: each adapter builds one from its own properties and carries its own Resilience4j
 * annotations. Never logs request or response bodies (they hold user text and audio).
 */
@Slf4j
final class BhashiniPipelineClient {

	private final RestClient client;
	private final String configUrl;
	private final String userId;
	private final String ulcaApiKey;
	private final String pipelineId;
	private final Duration configTtl;
	private final Clock clock;
	private final Map<String, PipelineConfig> configs = new ConcurrentHashMap<>();

	BhashiniPipelineClient(String configUrl, String userId, String ulcaApiKey, String pipelineId,
			Duration timeout, Duration configTtl, Clock clock) {
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(timeout);
		factory.setReadTimeout(timeout);
		this.client = RestClient.builder().requestFactory(factory).build();
		this.configUrl = configUrl;
		this.userId = userId;
		this.ulcaApiKey = ulcaApiKey;
		this.pipelineId = pipelineId;
		this.configTtl = configTtl;
		this.clock = clock;
	}

	/**
	 * Runs one pipeline task. {@code language} is the task's language block ({@code sourceLanguage}, plus
	 * {@code targetLanguage} for translation); {@code extraConfig} adds task options such as
	 * {@code audioFormat} or {@code gender}; {@code inputData} is sent as is.
	 */
	ComputeResponse compute(String taskType, Map<String, String> language, Map<String, Object> extraConfig,
			Map<String, Object> inputData) {
		String key = cacheKey(taskType, language);
		PipelineConfig config = config(taskType, language, key);
		Map<String, Object> taskConfig = new LinkedHashMap<>();
		taskConfig.put("language", language);
		taskConfig.put("serviceId", config.serviceId());
		taskConfig.putAll(extraConfig);
		Map<String, Object> body = Map.of(
				"pipelineTasks", List.of(Map.of("taskType", taskType, "config", taskConfig)),
				"inputData", inputData);
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
				configs.remove(key);
			}
			throw ex;
		}
		if (response == null || response.pipelineResponse() == null || response.pipelineResponse().isEmpty()) {
			throw new IllegalStateException("Bhashini returned no " + taskType + " output");
		}
		return response;
	}

	private PipelineConfig config(String taskType, Map<String, String> language, String key) {
		PipelineConfig cached = configs.get(key);
		if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
			return cached;
		}
		Map<String, Object> body = Map.of(
				"pipelineTasks", List.of(Map.of("taskType", taskType, "config", Map.of("language", language))),
				"pipelineRequestConfig", Map.of("pipelineId", pipelineId));
		ConfigResponse response = client.post()
				.uri(configUrl)
				.contentType(MediaType.APPLICATION_JSON)
				.header("userID", userId)
				.header("ulcaApiKey", ulcaApiKey)
				.body(body)
				.retrieve()
				.body(ConfigResponse.class);
		PipelineConfig fresh = PipelineConfig.from(response, taskType, language, clock.instant().plus(configTtl));
		configs.put(key, fresh);
		log.info("Bhashini pipeline config cached for {} (serviceId {})", key, fresh.serviceId());
		return fresh;
	}

	private static String cacheKey(String taskType, Map<String, String> language) {
		return taskType + ":" + new TreeMap<>(language);
	}

	static boolean isBlank(String s) {
		return s == null || s.isBlank();
	}

	/** What a config call resolves for one task and language. */
	record PipelineConfig(String serviceId, String endpoint, String keyName, String keyValue, Instant expiresAt) {

		static PipelineConfig from(ConfigResponse r, String taskType, Map<String, String> language, Instant expiresAt) {
			if (r == null || r.pipelineInferenceAPIEndPoint() == null
					|| r.pipelineInferenceAPIEndPoint().callbackUrl() == null
					|| r.pipelineInferenceAPIEndPoint().inferenceApiKey() == null) {
				throw new IllegalStateException("Bhashini config response has no inference endpoint");
			}
			String serviceId = null;
			for (ConfigResponse.TaskConfig task : r.pipelineResponseConfig() == null ? List.<ConfigResponse.TaskConfig>of()
					: r.pipelineResponseConfig()) {
				if (!taskType.equals(task.taskType()) || task.config() == null) {
					continue;
				}
				for (ConfigResponse.ServiceConfig c : task.config()) {
					boolean matches = c.language() != null && c.language().matches(language);
					if (serviceId == null || matches) {
						serviceId = c.serviceId();
					}
					if (matches) {
						break;
					}
				}
			}
			if (serviceId == null) {
				throw new IllegalStateException("Bhashini has no " + taskType + " service for " + language);
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

			boolean matches(Map<String, String> wanted) {
				return wanted.get("sourceLanguage").equals(sourceLanguage)
						&& (!wanted.containsKey("targetLanguage") || wanted.get("targetLanguage").equals(targetLanguage));
			}
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Endpoint(String callbackUrl, Key inferenceApiKey) {
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Key(String name, String value) {
		}
	}

	/**
	 * Compute response: text tasks fill {@code output[{source, target}]} (ASR puts its transcript in
	 * {@code source}); TTS fills {@code audio[{audioContent}]} with base64 audio.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record ComputeResponse(List<TaskOutput> pipelineResponse) {

		TaskOutput first() {
			return pipelineResponse.get(0);
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record TaskOutput(String taskType, List<Output> output, List<Audio> audio) {

			Output firstOutput() {
				return output == null || output.isEmpty() ? null : output.get(0);
			}

			String firstAudioContent() {
				return audio == null || audio.isEmpty() ? null : audio.get(0).audioContent();
			}
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Output(String source, String target) {
		}

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Audio(String audioContent) {
		}
	}
}
