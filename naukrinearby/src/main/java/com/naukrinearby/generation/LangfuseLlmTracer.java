package com.naukrinearby.generation;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.naukrinearby.config.LlmTraceProperties;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Sends traces to Langfuse's public ingestion API ({@code POST /api/public/ingestion}, basic auth with
 * the project's public/secret key). Fire-and-forget on a virtual thread: tracing can never slow down
 * or fail a resume parse. Only metadata is sent (see {@link LlmTrace}).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "naukri.llm-trace.provider", havingValue = "langfuse")
public class LangfuseLlmTracer implements LlmTracer {

	private final RestClient client;
	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	public LangfuseLlmTracer(LlmTraceProperties props) {
		if (props.host() == null || props.host().isBlank()) {
			throw new IllegalStateException("naukri.llm-trace.host (LANGFUSE_HOST) is required for provider=langfuse");
		}
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(props.timeout());
		factory.setReadTimeout(props.timeout());
		String credentials = nz(props.publicKey()) + ":" + nz(props.secretKey());
		this.client = RestClient.builder()
				.requestFactory(factory)
				.baseUrl(props.host())
				.defaultHeader("Authorization", "Basic "
						+ Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
				.build();
	}

	@Override
	public void record(LlmTrace trace) {
		executor.execute(() -> send(trace));
	}

	private void send(LlmTrace t) {
		try {
			client.post().uri("/api/public/ingestion")
					.contentType(MediaType.APPLICATION_JSON)
					.body(batch(t))
					.retrieve()
					.toBodilessEntity();
		}
		catch (RuntimeException ex) {
			log.debug("Langfuse trace not sent: {}", ex.getMessage());
		}
	}

	static Map<String, Object> batch(LlmTrace t) {
		String now = Instant.now().toString();
		String start = Instant.now().minusMillis(t.latencyMs()).toString();
		String traceId = UUID.randomUUID().toString();

		Map<String, Object> traceBody = new LinkedHashMap<>();
		traceBody.put("id", traceId);
		traceBody.put("name", t.name());
		traceBody.put("timestamp", start);
		traceBody.put("metadata", Map.of("promptVersion", nz(t.promptVersion())));

		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("provider", nz(t.provider()));
		metadata.put("jsonValid", t.jsonValid());
		metadata.put("inputChars", t.inputChars());
		metadata.put("outputItems", t.outputItems());

		Map<String, Object> generation = new LinkedHashMap<>();
		generation.put("id", UUID.randomUUID().toString());
		generation.put("traceId", traceId);
		generation.put("name", t.name());
		generation.put("model", nz(t.model()));
		generation.put("startTime", start);
		generation.put("endTime", now);
		generation.put("level", t.jsonValid() ? "DEFAULT" : "ERROR");
		generation.put("version", nz(t.promptVersion()));
		generation.put("metadata", metadata);

		return Map.of("batch", List.of(
				event("trace-create", now, traceBody),
				event("generation-create", now, generation)));
	}

	private static Map<String, Object> event(String type, String timestamp, Map<String, Object> body) {
		Map<String, Object> e = new LinkedHashMap<>();
		e.put("id", UUID.randomUUID().toString());
		e.put("type", type);
		e.put("timestamp", timestamp);
		e.put("body", body);
		return e;
	}

	@PreDestroy
	void shutdown() {
		executor.shutdown();
	}

	private static String nz(String s) {
		return s == null ? "" : s;
	}
}
