package com.naukrinearby.generation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Default: tracing off. */
@Component
@ConditionalOnProperty(name = "naukri.llm-trace.provider", havingValue = "none", matchIfMissing = true)
public class NoopLlmTracer implements LlmTracer {

	@Override
	public void record(LlmTrace trace) {
		// intentionally empty
	}
}
