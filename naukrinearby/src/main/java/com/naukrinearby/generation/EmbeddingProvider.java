package com.naukrinearby.generation;

/**
 * Provider-agnostic text embedding (master plan §2). Default impl is a deterministic local model so
 * pgvector matching works offline with zero per-call cost; a remote bge-m3/HTTP impl can be added
 * behind this interface and selected via {@code naukri.embedding.provider}.
 */
public interface EmbeddingProvider {

	float[] embed(String text);

	int dims();

	String providerId();
}
