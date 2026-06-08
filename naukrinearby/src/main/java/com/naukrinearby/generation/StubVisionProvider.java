package com.naukrinearby.generation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Placeholder OCR. The real {@link LlmVisionProvider} replaces this when
 * {@code naukri.vision.provider=llm}; the stub stays the default. The MVP focuses LLM spend on text
 * extraction; image OCR is stubbed.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "naukri.vision.provider", havingValue = "stub", matchIfMissing = true)
public class StubVisionProvider implements VisionProvider {

	@Override
	public String extractText(byte[] image, String instruction) {
		log.warn("Vision OCR is stubbed — returning empty text for an image of {} bytes",
				image == null ? 0 : image.length);
		return "";
	}

	@Override
	public String providerId() {
		return "stub-vision";
	}
}
