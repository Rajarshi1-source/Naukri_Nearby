package com.naukrinearby.service.trust;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChecksumGstVerifierTest {

	private final ChecksumGstVerifier verifier = new ChecksumGstVerifier();

	@Test
	void acceptsPublishedSampleGstins() {
		// Sample GSTINs widely used in GST documentation; their check characters are V and 4.
		assertThat(verifier.isValid("27AAPFU0939F1ZV")).isTrue();
		assertThat(verifier.isValid("29AAGCB7383J1Z4")).isTrue();
		assertThat(verifier.isValid(" 27aapfu0939f1zv ")).isTrue();
	}

	@Test
	void rejectsWrongCheckCharacter() {
		assertThat(verifier.isValid("27AAPFU0939F1ZW")).isFalse();
	}

	@Test
	void rejectsBadFormatAndStateCodes() {
		assertThat(verifier.isValid(null)).isFalse();
		assertThat(verifier.isValid("")).isFalse();
		assertThat(verifier.isValid("27AAPFU0939F1Z")).isFalse();
		assertThat(verifier.isValid("27AAPFU0939F1XV")).isFalse();
		assertThat(verifier.isValid("00AAPFU0939F1ZV")).isFalse();
		assertThat(verifier.isValid("55AAPFU0939F1ZV")).isFalse();
	}

	@Test
	void checkCharacterMatchesKnownSamples() {
		assertThat(ChecksumGstVerifier.checkCharacter("27AAPFU0939F1Z")).isEqualTo('V');
		assertThat(ChecksumGstVerifier.checkCharacter("29AAGCB7383J1Z")).isEqualTo('4');
	}
}
