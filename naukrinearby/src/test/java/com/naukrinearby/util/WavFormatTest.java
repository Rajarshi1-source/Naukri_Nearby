package com.naukrinearby.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Header checks for the voice-search upload: format, duration, odd chunks and hostile sizes. */
class WavFormatTest {

	@Test
	void readsA16kMonoClip_andItsDuration() {
		WavFormat wav = WavFormat.parse(WavFixtures.voiceSearchClip(2.5)).orElseThrow();
		assertThat(wav.isPcm16Mono(16_000)).isTrue();
		assertThat(wav.durationSeconds()).isEqualTo(2.5);
	}

	@Test
	void rejectsOtherFormats() {
		assertThat(WavFormat.parse(WavFixtures.silence(44_100, 2, 16, 0.1)).orElseThrow().isPcm16Mono(16_000)).isFalse();
		assertThat(WavFormat.parse(WavFixtures.silence(16_000, 1, 8, 0.1)).orElseThrow().isPcm16Mono(16_000)).isFalse();
	}

	@Test
	void skipsUnknownChunks_beforeData() {
		byte[] clip = WavFixtures.voiceSearchClip(0.1);
		// A LIST chunk with an odd size (so one pad byte) between fmt and data.
		ByteBuffer list = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
		list.put("LIST".getBytes(StandardCharsets.US_ASCII)).putInt(3).put(new byte[] { 1, 2, 3, 0 });
		byte[] withList = new byte[clip.length + 12];
		System.arraycopy(clip, 0, withList, 0, 36);
		System.arraycopy(list.array(), 0, withList, 36, 12);
		System.arraycopy(clip, 36, withList, 48, clip.length - 36);

		WavFormat wav = WavFormat.parse(withList).orElseThrow();
		assertThat(wav.sampleRate()).isEqualTo(16_000);
		assertThat(wav.durationSeconds()).isEqualTo(0.1);
	}

	@Test
	void rejectsGarbage_truncatedHeaders_andOverlongChunks() {
		assertThat(WavFormat.parse(null)).isEmpty();
		assertThat(WavFormat.parse("not a wav file at all".getBytes(StandardCharsets.US_ASCII))).isEmpty();
		assertThat(WavFormat.parse(Arrays.copyOf(WavFixtures.voiceSearchClip(0.1), 30))).isEmpty();
		byte[] lying = WavFixtures.voiceSearchClip(0.1);
		ByteBuffer.wrap(lying).order(ByteOrder.LITTLE_ENDIAN).putInt(16, Integer.MAX_VALUE); // fmt chunk size
		assertThat(WavFormat.parse(lying)).isEmpty();
	}

	@Test
	void dataSize_isClampedToWhatWasUploaded() {
		byte[] clip = WavFixtures.voiceSearchClip(1);
		ByteBuffer.wrap(clip).order(ByteOrder.LITTLE_ENDIAN).putInt(40, Integer.MAX_VALUE); // data chunk size
		assertThat(WavFormat.parse(clip).orElseThrow().durationSeconds()).isEqualTo(1.0);
	}
}
