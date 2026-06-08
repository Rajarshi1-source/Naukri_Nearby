package com.naukrinearby.service;

import java.util.Locale;

import com.naukrinearby.generation.EmbeddingProvider;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Generates embeddings and renders them as a pgvector literal (e.g. {@code [0.1,0.2,...]}). */
@Service
@RequiredArgsConstructor
public class EmbeddingService {

	private final EmbeddingProvider provider;

	public float[] generateEmbedding(String text) {
		return provider.embed(text);
	}

	/** pgvector literal accepted by {@code CAST(:vec AS vector)}; null if the text yields no signal. */
	public String toVectorLiteral(String text) {
		return toLiteral(generateEmbedding(text));
	}

	public String toLiteral(float[] vec) {
		if (vec == null || vec.length == 0) {
			return null;
		}
		StringBuilder sb = new StringBuilder(vec.length * 8);
		sb.append('[');
		for (int i = 0; i < vec.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(String.format(Locale.US, "%.6f", vec[i]));
		}
		sb.append(']');
		return sb.toString();
	}
}
