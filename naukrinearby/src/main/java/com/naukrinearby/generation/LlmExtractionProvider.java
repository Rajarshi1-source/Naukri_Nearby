package com.naukrinearby.generation;

/**
 * Provider-agnostic structured extraction (master plan §2). Implementations call a model at
 * temperature 0 with JSON output and map the result onto the schema. Selected via
 * {@code naukri.llm.extraction-provider}.
 */
public interface LlmExtractionProvider {

	<T> T extractStructured(String prompt, Class<T> schema, String promptVersion);

	String providerId();
}
