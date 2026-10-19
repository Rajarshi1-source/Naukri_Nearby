package com.naukrinearby.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Builds silent PCM WAV clips for tests. */
public final class WavFixtures {

	private WavFixtures() {
	}

	public static byte[] silence(int sampleRate, int channels, int bitsPerSample, double seconds) {
		int dataBytes = (int) (sampleRate * channels * (bitsPerSample / 8) * seconds);
		ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
		b.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + dataBytes)
				.put("WAVE".getBytes(StandardCharsets.US_ASCII))
				.put("fmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
				.putShort((short) 1).putShort((short) channels).putInt(sampleRate)
				.putInt(sampleRate * channels * bitsPerSample / 8)
				.putShort((short) (channels * bitsPerSample / 8)).putShort((short) bitsPerSample)
				.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(dataBytes);
		return b.array();
	}

	public static byte[] voiceSearchClip(double seconds) {
		return silence(16_000, 1, 16, seconds);
	}
}
