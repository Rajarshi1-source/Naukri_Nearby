package com.naukrinearby.generation;

/**
 * LLM observability port (plan D13). One bean is active, chosen by {@code naukri.llm-trace.provider}:
 * {@code none} (default), {@code log}, or {@code langfuse}. Implementations must never throw into the
 * caller and must never receive personal data — a {@link LlmTrace} carries metadata only.
 */
public interface LlmTracer {

	void record(LlmTrace trace);

	/**
	 * Metadata for one LLM call. No prompt or completion text: resumes are personal data (DPDP), so
	 * only sizes, the prompt version and outcome are traced.
	 *
	 * @param name logical operation, e.g. {@code resume-extraction}
	 * @param inputChars length of the rendered prompt
	 * @param outputItems number of skills extracted (a cheap quality signal), or -1 on failure
	 */
	record LlmTrace(String name, String provider, String model, String promptVersion, long latencyMs,
			boolean jsonValid, int inputChars, int outputItems) {
	}
}
