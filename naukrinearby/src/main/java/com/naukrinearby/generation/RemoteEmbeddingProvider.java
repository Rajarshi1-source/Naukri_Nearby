package com.naukrinearby.generation;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.naukrinearby.config.EmbeddingProperties;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real embedding provider calling an OpenAI-compatible {@code /embeddings} endpoint (e.g. a hosted
 * bge-m3 or a HuggingFace TEI server). Selected by {@code naukri.embedding.provider=remote}; the
 * deterministic {@link LocalEmbeddingProvider} stays the default. The returned vector dimension MUST
 * equal {@code naukri.embedding.dims} (1024 for bge-m3) to match the pgvector column.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "naukri.embedding.provider", havingValue = "remote")
public class RemoteEmbeddingProvider implements EmbeddingProvider {

	private final EmbeddingProperties props;
	private final RestClient client;

	public RemoteEmbeddingProvider(EmbeddingProperties props) {
		this.props = props;
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(10_000);
		factory.setReadTimeout(30_000);
		this.client = RestClient.builder()
				.baseUrl(props.baseUrl() == null ? "" : props.baseUrl())
				.requestFactory(factory)
				.defaultHeader("Authorization", "Bearer " + (props.apiKey() == null ? "" : props.apiKey()))
				.build();
	}

	@Override
	@Retry(name = "embedding")
	@CircuitBreaker(name = "embedding")
	public float[] embed(String text) {
		if (text == null || text.isBlank()) {
			return new float[props.dims()];
		}
		Map<String, Object> body = Map.of("model", props.model(), "input", text);
		EmbeddingResponse response = client.post()
				.uri("/embeddings")
				.body(body)
				.retrieve()
				.body(EmbeddingResponse.class);
		if (response == null || response.data() == null || response.data().isEmpty()) {
			throw new IllegalStateException("Empty embedding response from " + props.baseUrl());
		}
		List<Float> raw = response.data().get(0).embedding();
		if (raw == null || raw.size() != props.dims()) {
			throw new IllegalStateException("Embedding dim mismatch: model returned "
					+ (raw == null ? 0 : raw.size()) + " but naukri.embedding.dims=" + props.dims());
		}
		float[] vec = new float[raw.size()];
		for (int i = 0; i < raw.size(); i++) {
			vec[i] = raw.get(i);
		}
		return vec;
	}

	@Override
	public int dims() {
		return props.dims();
	}

	@Override
	public String providerId() {
		return "remote:" + props.model();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record EmbeddingResponse(List<Item> data) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Item(List<Float> embedding) {
	}
}
