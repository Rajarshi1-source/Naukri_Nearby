package com.naukrinearby.generation;

import com.naukrinearby.config.EmbeddingProperties;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic offline embedding: hashes tokens (uni- and bi-grams) into a fixed-dimension vector
 * and L2-normalizes. Shared tokens produce higher cosine similarity, which is enough for demo-quality
 * semantic matching without any external embedding service. Selected by
 * {@code naukri.embedding.provider=local} (the default).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "naukri.embedding.provider", havingValue = "local", matchIfMissing = true)
public class LocalEmbeddingProvider implements EmbeddingProvider {

	private final EmbeddingProperties props;

	@Override
	public float[] embed(String text) {
		float[] v = new float[props.dims()];
		if (text == null || text.isBlank()) {
			return v;
		}
		String[] tokens = text.toLowerCase().split("[^a-z0-9\\u0900-\\u097F]+");
		String previous = null;
		for (String token : tokens) {
			if (token.isBlank()) {
				continue;
			}
			addHashed(v, token);
			if (previous != null) {
				addHashed(v, previous + "_" + token);
			}
			previous = token;
		}
		normalize(v);
		return v;
	}

	private void addHashed(float[] v, String token) {
		int idx = Math.floorMod(token.hashCode(), v.length);
		int sign = (token.hashCode() & 1) == 0 ? 1 : -1;
		v[idx] += sign;
	}

	private void normalize(float[] v) {
		double norm = 0;
		for (float x : v) {
			norm += (double) x * x;
		}
		norm = Math.sqrt(norm);
		if (norm > 0) {
			for (int i = 0; i < v.length; i++) {
				v[i] = (float) (v[i] / norm);
			}
		}
	}

	@Override
	public int dims() {
		return props.dims();
	}

	@Override
	public String providerId() {
		return "local";
	}
}
