package com.naukrinearby.generation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Placeholder OCR. A real implementation (a vision LLM or Google Vision / Tesseract) can replace
 * this behind {@link VisionProvider}. The MVP focuses LLM spend on text extraction; image OCR is stubbed.
 */
@Slf4j
@Component
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
