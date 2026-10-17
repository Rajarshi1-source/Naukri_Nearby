package com.naukrinearby.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** A supported language code (see {@code Language}): en, hi, ta, te, bn, mr, kn, gu. */
public record LanguageRequest(@NotBlank @Pattern(regexp = "en|hi|ta|te|bn|mr|kn|gu") String language) {
}
