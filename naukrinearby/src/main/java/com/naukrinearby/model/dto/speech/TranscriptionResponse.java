package com.naukrinearby.model.dto.speech;

/** Voice-search transcript. Empty {@code text} means nothing was recognised (or the stub provider). */
public record TranscriptionResponse(String text, String language, String provider) {
}
