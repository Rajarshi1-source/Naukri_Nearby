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

	@Override
	protected String callModelRaw(String prompt) {
		// Retry + circuit breaking are applied by Resilience4j on the public extractStructured(...)
		// entry point (see AbstractLlmExtractionProvider); this method does a single HTTP call.
		Map<String, Object> body = Map.of(
				"model", props.extractionModel(),
				"temperature", props.temperature(),
				"response_format", Map.of("type", "json_object"),
				"messages", List.of(Map.of("role", "user", "content", prompt)));
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
