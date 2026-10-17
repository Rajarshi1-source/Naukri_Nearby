package com.naukrinearby.model.dto.job;

/**
 * A job's title and description in another language. {@code translated} is false when the original is
 * returned instead (same language, provider unavailable, or the stub provider), so the UI can say so
 * rather than present English as a translation.
 */
public record JobTranslationResponse(
		Long jobId,
		String language,
		String sourceLanguage,
		String title,
		String description,
		boolean translated,
		String provider) {
}
