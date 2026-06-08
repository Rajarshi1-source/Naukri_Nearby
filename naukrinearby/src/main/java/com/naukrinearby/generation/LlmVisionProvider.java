package com.naukrinearby.generation;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.naukrinearby.config.LlmProperties;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Real OCR via an OpenAI-compatible multimodal chat endpoint: the image is sent as a base64 data URL
 * and the model is asked to transcribe it (Indian resumes are often photos with mixed Hindi/English).
 * Selected by {@code naukri.vision.provider=llm}; reuses {@code naukri.llm.*} (base-url, api-key,
 * vision-model). The {@link StubVisionProvider} stays the default.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "naukri.vision.provider", havingValue = "llm")
public class LlmVisionProvider implements VisionProvider {

	private final LlmProperties props;
	private final RestClient client;

	public LlmVisionProvider(LlmProperties props) {
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
	public String extractText(byte[] image, String instruction) {
		if (image == null || image.length == 0) {
			return "";
		}
		String dataUrl = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image);
		Map<String, Object> content = Map.of(
				"role", "user",
				"content", List.of(
						Map.of("type", "text", "text", instruction == null
								? "Extract all text from this Indian resume image; transliterate Hindi/regional text to English."
								: instruction),
						Map.of("type", "image_url", "image_url", Map.of("url", dataUrl))));
		Map<String, Object> body = Map.of(
				"model", props.visionModel(),
				"temperature", 0.0,
				"messages", List.of(content));
		try {
			ChatResponse response = client.post()
					.uri("/chat/completions")
					.body(body)
					.retrieve()
					.body(ChatResponse.class);
			if (response == null || response.choices() == null || response.choices().isEmpty()) {
				return "";
			}
			String text = response.choices().get(0).message().content();
			return text == null ? "" : text;
		}
		catch (RuntimeException ex) {
			log.warn("Vision OCR failed: {}", ex.getMessage());
			return "";
		}
	}

	@Override
	public String providerId() {
		return "llm:" + props.visionModel();
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
