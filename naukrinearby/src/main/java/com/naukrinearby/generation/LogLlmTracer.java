package com.naukrinearby.generation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** One structured log line per LLM call, for a log pipeline to pick up. */
@Slf4j
@Component
@ConditionalOnProperty(name = "naukri.llm-trace.provider", havingValue = "log")
public class LogLlmTracer implements LlmTracer {

	@Override
	public void record(LlmTrace t) {
		log.info("llm_trace name={} provider={} model={} promptVersion={} latencyMs={} jsonValid={} inputChars={} outputItems={}",
				t.name(), t.provider(), t.model(), t.promptVersion(), t.latencyMs(), t.jsonValid(), t.inputChars(),
				t.outputItems());
	}
}
