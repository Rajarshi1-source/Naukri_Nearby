package com.naukrinearby.generation;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.naukrinearby.config.LlmProperties;
import com.naukrinearby.exception.ResumeParseException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real OpenAI-compatible chat-completions provider (mid-tier model, temperature 0, JSON mode).
 * Selected by {@code naukri.llm.extraction-provider=gpt5mini} (the default). Point {@code naukri.llm.base-url}
 * at any OpenAI-compatible endpoint and supply {@code naukri.llm.api-key}.
 */
@Component
@ConditionalOnProperty(name = "naukri.llm.extraction-provider", havingValue = "gpt5mini", matchIfMissing = true)
public class Gpt5MiniProvider extends AbstractLlmExtractionProvider {

	private final LlmProperties props;
	private final RestClient client;

	public Gpt5MiniProvider(ObjectMapper objectMapper, LlmProperties props) {
		super(objectMapper);
		this.props = props;
		var factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout((int) Math.min(Integer.MAX_VALUE, props.timeout().toMillis()));
		factory.setReadTimeout((int) Math.min(Integer.MAX_VALUE, props.timeout().toMillis()));
		this.client = RestClient.builder()
				.baseUrl(props.baseUrl())
				.requestFactory(factory)
				.defaultHeader("Authorization", "Bearer " + (props.apiKey() == null ? "" : props.apiKey()))
				.build();
	}

	private static final int MAX_ATTEMPTS = 3;

	@Override
	protected String callModelRaw(String prompt) {
		Map<String, Object> body = Map.of(
				"model", props.extractionModel(),
				"temperature", props.temperature(),
				"response_format", Map.of("type", "json_object"),
				"messages", List.of(Map.of("role", "user", "content", prompt)));

		// Retry with exponential backoff on transient failures (timeouts / 5xx / network).
		RuntimeException last = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				ChatResponse response = client.post()
						.uri("/chat/completions")
						.body(body)
						.retrieve()
						.body(ChatResponse.class);
				if (response == null || response.choices() == null || response.choices().isEmpty()) {
					throw new ResumeParseException("Empty response from LLM");
				}
				return response.choices().get(0).message().content();
			}
			catch (RuntimeException ex) {
				last = ex;
				if (attempt < MAX_ATTEMPTS) {
					sleep(200L * (1L << (attempt - 1)));
				}
			}
		}
		throw new ResumeParseException("LLM call failed after " + MAX_ATTEMPTS + " attempts", last);
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		}
		catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public String providerId() {
		return "gpt5mini";
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ChatResponse(List<Choice> choices) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Choice(Message message) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Message(String role, String content) {
	}
}
